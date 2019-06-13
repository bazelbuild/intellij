/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.run;

import com.google.idea.blaze.base.settings.Blaze;
import com.intellij.debugger.NoDataException;
import com.intellij.debugger.PositionManager;
import com.intellij.debugger.PositionManagerFactory;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.engine.JVMNameUtil;
import com.intellij.debugger.engine.PositionManagerImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.lang.jvm.JvmParameter;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaRecursiveElementVisitor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.source.PsiParameterImpl;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.Location;
import com.sun.jdi.Method;
import com.sun.jdi.ReferenceType;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/**
 * This position manager only handles source positions of android support library classes that have
 * not yet been migrated to androidx. In the event where the binary used to compile the program is
 * an old support library binary, this position manager will try to show the sources of the androidx
 * equivalent class instead.
 */
public class JetifiedSupportLibCompatibilityPositionManager extends PositionManagerImpl {
  private final JetifierClassNameTransformer classNameTransformer;

  JetifiedSupportLibCompatibilityPositionManager(DebugProcessImpl debugProcess) {
    super(debugProcess);
    classNameTransformer = new JetifierClassNameTransformer();
    classNameTransformer.loadJetpackTransformations(debugProcess.getProject());
  }

  // throw NoDataException instead of returning null like PositionManagerImpl does because
  // doing this allows correct fallback behaviour.  This prevents this position manager
  // from completely overshadowing other ones registered after it.
  @Override
  @Nullable
  public SourcePosition getSourcePosition(final Location location) throws NoDataException {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    if (location == null) {
      throw NoDataException.INSTANCE;
    }

    final Method jdiMethod = DebuggerUtilsEx.getMethod(location);
    if (jdiMethod == null || jdiMethod.name() == null || jdiMethod.signature() == null) {
      throw NoDataException.INSTANCE;
    }

    Project project = getDebugProcess().getProject();
    PsiFile psiFile = getDejetifiedPsiFileByLocation(project, location);
    if (psiFile == null) {
      throw NoDataException.INSTANCE;
    }

    // The line number we get from the debugger won't be correct due to changes made during
    // de-jetification of androidx binaries.  Try to find the offset and compensate for it.
    // The line number offset is calculated as:
    // offset = first line of method from debugger - first line of method in the psi document
    final ArrayList<PsiMethod> psiMethodCandidates = new ArrayList<>();
    psiFile.accept(
        new JavaRecursiveElementVisitor() {
          @Override
          public void visitMethod(PsiMethod psiMethod) {
            super.visitMethod(psiMethod);
            if (psiMethod.getName().equals(jdiMethod.name())) {
              psiMethodCandidates.add(psiMethod);
            }
          }
        });

    PsiMethod psiMethod = null;
    for (PsiMethod candidate : psiMethodCandidates) {
      if (guessMethodEquivalence(candidate, jdiMethod)) {
        psiMethod = candidate;
        break;
      }
    }

    if (psiMethod == null) {
      throw NoDataException.INSTANCE;
    }

    // Here I use psiMethod.getBody().getLBrace() because it seems to yield the most number of
    // matched line numbers.  I don't know why this is the case and I have not dug too deep into it.
    // If we run into bugs where line numbers are mismatching this is a good place to start.
    Document document = PsiDocumentManager.getInstance(project).getDocument(psiFile);
    int psiMethodStartingLine =
        document.getLineNumber(psiMethod.getBody().getLBrace().getTextOffset());
    int jdiMethodStartingLine = jdiMethod.location().lineNumber();
    int offset = psiMethodStartingLine - jdiMethodStartingLine + 1; // jdi line numbers are 0 based

    return SourcePosition.createFromLine(
        psiFile, DebuggerUtilsEx.getLineNumber(location, false) + offset);
  }

  /**
   * Guesses whether a candidate PSI method is equivalent to the given JDI method.
   *
   * <p>It's not always possible to precisely determine whether the methods are equivalent. In such
   * cases, this method uses heuristics to make a best guess. For example:
   *
   * <p>1. Generics. The psi method may use some generic argument, whereas the jdi Method will
   * always use the types resolved in it's current runtime. An example of this is
   * RecyclerView$Adapter#bindViewHolder. The first argument is a generic argument called VH that
   * extends ViewHolder. We can't simply take its type name, because it would just be "VH". In this
   * case we try to find it in the generic type's superclass type names and hope they match. (In
   * practice they very often do)
   *
   * <p>2. Literal type names. The psi method only knows the type it's declared with. This means if
   * the type is declared as something like OuterClass.InnerCass, then that's literally what the
   * type is called when we call thatType.getCanonicalText() and there isn't a way to resolve the
   * type to it's fully qualified name. Fortunately in most of these cases the jdi type name (which
   * is fully qualified) ends with the type name from the psi method. E.g.
   * package.OuterClass.InnerClass ends with OuterClass.InnerClass.
   *
   * <p>Maintainers of this method should expect to keep this method up to date as more known
   * edge-cases are discovered.
   *
   * @return true if we think the methods references refer to the same method.
   */
  private boolean guessMethodEquivalence(PsiMethod psiMethod, Method jdiMethod) {
    if (!psiMethod.getName().equals(jdiMethod.name())) {
      return false;
    }

    JvmParameter[] jvmParams = psiMethod.getParameters();
    List<String> jdiMethodTypeNames = jdiMethod.argumentTypeNames();

    if (jvmParams.length != jdiMethodTypeNames.size()) {
      return false;
    }

    iterate_params:
    for (int i = 0; i < jvmParams.length; i++) {
      if (!(jvmParams[i] instanceof PsiParameterImpl)) {
        return false;
      }

      PsiParameterImpl psiParam = (PsiParameterImpl) jvmParams[i];

      // Sanitize the type name to dot separated names. (e.g. package.Class$InnerClass ->
      // package.Class.InnerClass)
      String jdiTypeName = getJetifiedClassName(jdiMethodTypeNames.get(i));
      if (jdiTypeName == null) {
        jdiTypeName = jdiMethodTypeNames.get(i);
      }
      jdiTypeName = jdiTypeName.replace("$", ".");

      // Generics info need to be stripped because they are not present in the jdi type name.
      if (jdiTypeName.endsWith(stripGenericInfo(psiParam.getType().getCanonicalText()))) {
        continue;
      }

      // If the type name does not match it could be because the type is a generic type.
      // See if one of the type's superclass matches.
      for (PsiType psiSuperType : psiParam.getType().getSuperTypes()) {
        if (jdiTypeName.endsWith(stripGenericInfo(psiSuperType.getCanonicalText()))) {
          continue iterate_params;
        }
      }

      return false;
    }

    return true;
  }

  /**
   * Strips a type info of format "{@code package.Class<GenericA, GenericB, ...>}" to just
   * "package.Class".
   */
  private static String stripGenericInfo(String typeInfo) {
    if (typeInfo.indexOf('<') != -1) {
      return typeInfo.substring(0, typeInfo.indexOf('<'));
    }
    return typeInfo;
  }

  @org.jetbrains.annotations.Nullable
  private PsiFile getDejetifiedPsiFileByLocation(final Project project, final Location location) {
    if (location == null) {
      return null;
    }
    final ReferenceType refType = location.declaringType();
    if (refType == null) {
      return null;
    }

    final String jetifiedQName = getJetifiedClassName(refType.name());

    if (jetifiedQName == null) {
      return null;
    }

    try {
      PsiFile[] files =
          FilenameIndex.getFilesByName(
              project, refType.sourceName(), GlobalSearchScope.allScope(project));
      for (PsiFile file : files) {
        if (file instanceof PsiJavaFile) {
          for (PsiClass cls : PsiTreeUtil.findChildrenOfAnyType(file, PsiClass.class)) {
            if (StringUtil.equals(jetifiedQName, JVMNameUtil.getClassVMName(cls))) {
              return file;
            }
          }
        }
      }
    } catch (AbsentInformationException ignore) {
      return null;
    }

    return null;
  }

  private String getJetifiedClassName(String originalName) {
    int splitPosition =
        originalName.indexOf('$') > 0 ? originalName.indexOf('$') : originalName.indexOf(':');
    if (splitPosition == -1) {
      splitPosition = originalName.length();
    }

    String jetifiedClassName =
        classNameTransformer.getTransformedClassName(originalName.substring(0, splitPosition));
    if (jetifiedClassName != null) {
      return jetifiedClassName + originalName.substring(splitPosition);
    }

    return null;
  }

  static class Factory extends PositionManagerFactory {
    @Nullable
    @Override
    public PositionManager createPositionManager(DebugProcess process) {
      return process instanceof DebugProcessImpl && Blaze.isBlazeProject(process.getProject())
          ? new JetifiedSupportLibCompatibilityPositionManager((DebugProcessImpl) process)
          : null;
    }
  }
}
