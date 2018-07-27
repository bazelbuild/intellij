/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.ijwb.typescript;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.io.VirtualFileSystemProvider;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.ExecutionRootPath;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.workspace.ExecutionRootPathResolver;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.lang.ecmascript6.psi.ES6ImportedBinding;
import com.intellij.lang.javascript.JSTokenTypes;
import com.intellij.lang.javascript.JavascriptLanguage;
import com.intellij.lang.javascript.psi.JSAssignmentExpression;
import com.intellij.lang.javascript.psi.JSCallExpression;
import com.intellij.lang.javascript.psi.JSDefinitionExpression;
import com.intellij.lang.javascript.psi.JSFile;
import com.intellij.lang.javascript.psi.JSFunction;
import com.intellij.lang.javascript.psi.JSLiteralExpression;
import com.intellij.lang.javascript.psi.JSNewExpression;
import com.intellij.lang.javascript.psi.JSObjectLiteralExpression;
import com.intellij.lang.javascript.psi.JSProperty;
import com.intellij.lang.javascript.psi.JSReferenceExpression;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptClass;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptEnum;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptEnumField;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptFunction;
import com.intellij.lang.javascript.psi.ecma6.TypeScriptModule;
import com.intellij.lang.javascript.psi.ecmal4.JSAttributeList.ModifierType;
import com.intellij.lang.javascript.psi.ecmal4.JSAttributeListOwner;
import com.intellij.lang.javascript.psi.ecmal4.JSClass;
import com.intellij.lang.javascript.psi.ecmal4.JSQualifiedNamedElement;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;

/** Handles .ts -> .js navigation. Resolving goes to generated .d.ts files otherwise. */
public class BlazeTypescriptGotoDeclarationHandler implements GotoDeclarationHandler {
  private static BoolExperiment typescriptGotoJavascript =
      new BoolExperiment("typescript.goto.javascript", true);

  @Nullable
  @Override
  public PsiElement[] getGotoDeclarationTargets(
      @Nullable PsiElement sourceElement, int offset, Editor editor) {
    if (!typescriptGotoJavascript.getValue()
        || sourceElement == null
        || !isJsIdentifier(sourceElement)) {
      return null;
    }
    Project project = sourceElement.getProject();
    BlazeProjectData projectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (!Blaze.isBlazeProject(project)
        || projectData == null
        || !projectData.workspaceLanguageSettings.isLanguageActive(LanguageClass.JAVASCRIPT)
        || !projectData.workspaceLanguageSettings.isLanguageActive(LanguageClass.TYPESCRIPT)) {
      return null;
    }
    JSReferenceExpression referenceExpression =
        PsiTreeUtil.getParentOfType(sourceElement, JSReferenceExpression.class);
    boolean isConstructor;
    Collection<PsiElement> resolvedToDts;
    if (referenceExpression != null) {
      isConstructor = referenceExpression.getParent() instanceof JSNewExpression;
      resolvedToDts = resolveToDts(referenceExpression);
    } else if (sourceElement.getParent() instanceof ES6ImportedBinding) {
      // The symbols in import statements aren't reference expressions. E.g.,
      // import {Foo} from 'goog:foo.bar.Foo';
      ES6ImportedBinding parent = (ES6ImportedBinding) sourceElement.getParent();
      isConstructor = false;
      resolvedToDts = parent.findReferencedElements();
    } else {
      return null;
    }
    PsiManager psiManager = PsiManager.getInstance(project);
    LocalFileSystem lfs = VirtualFileSystemProvider.getInstance().getSystem();
    ExecutionRootPathResolver pathResolver = ExecutionRootPathResolver.fromProject(project);
    if (pathResolver == null) {
      return null;
    }
    return resolvedToDts
        .stream()
        .map(e -> resolveToJs(pathResolver, lfs, psiManager, isConstructor, e))
        .flatMap(Collection::stream)
        .toArray(PsiElement[]::new);
  }

  private static boolean isJsIdentifier(PsiElement sourceElement) {
    return Optional.of(sourceElement)
        .filter(e -> e.getLanguage().is(JavascriptLanguage.INSTANCE))
        .filter(LeafPsiElement.class::isInstance)
        .map(LeafPsiElement.class::cast)
        .filter(e -> e.getElementType().equals(JSTokenTypes.IDENTIFIER))
        .isPresent();
  }

  private static Collection<PsiElement> resolveToDts(JSReferenceExpression referenceExpression) {
    return Stream.of(referenceExpression)
        .map(e -> e.multiResolve(false))
        .flatMap(Arrays::stream)
        .filter(ResolveResult::isValidResult)
        .map(ResolveResult::getElement)
        .filter(Objects::nonNull)
        .flatMap(
            e ->
                e instanceof ES6ImportedBinding
                    ? ((ES6ImportedBinding) e).findReferencedElements().stream()
                    : Stream.of(e))
        .collect(Collectors.toList());
  }

  private static Collection<PsiElement> resolveToJs(
      ExecutionRootPathResolver pathResolver,
      LocalFileSystem lfs,
      PsiManager psiManager,
      boolean isConstructor,
      PsiElement dtsElement) {
    dtsElement = PsiTreeUtil.getParentOfType(dtsElement, JSQualifiedNamedElement.class, false);
    if (dtsElement == null) {
      return ImmutableList.of();
    }
    PsiFile dtsFile = dtsElement.getContainingFile();
    if (!(dtsFile instanceof JSFile) || !dtsFile.getName().endsWith(".d.ts")) {
      return ImmutableList.of();
    }
    String qualifiedName = getDtsQualifiedName((JSQualifiedNamedElement) dtsElement);
    if (qualifiedName == null) {
      return ImmutableList.of();
    }
    Collection<JSFile> jsFiles =
        jsFilesFromDtsFile(pathResolver, lfs, psiManager, (JSFile) dtsFile);
    if (jsFiles.isEmpty()) {
      return ImmutableList.of();
    }
    if (dtsElement instanceof TypeScriptModule) {
      String moduleName = getModuleName(qualifiedName);
      return isConstructor
          ? findChildrenOfType(jsFiles, JSFunction.class)
              .stream()
              .filter(e -> isConstructorWithName(e, moduleName))
              .collect(Collectors.toList())
          : getModuleDeclarations(jsFiles)
              .stream()
              .filter(a -> Objects.equals(a.getStringValue(), moduleName))
              .collect(Collectors.toList());
    }
    return getResolveCandidates(dtsElement, jsFiles)
        .stream()
        .filter(e -> Objects.equals(getJsQualifiedName(e), qualifiedName))
        .collect(Collectors.toList());
  }

  private static Collection<? extends JSQualifiedNamedElement> getResolveCandidates(
      PsiElement dtsElement, Collection<JSFile> jsFiles) {
    if (dtsElement instanceof TypeScriptClass) {
      return Stream.concat(
              findChildrenOfType(jsFiles, JSClass.class).stream(),
              // Apparently you can declare a JS class with just a constructor function and
              // attach some properties to it.
              findChildrenOfType(jsFiles, JSFunction.class).stream())
          .collect(Collectors.toList());
    } else if (dtsElement instanceof TypeScriptFunction) {
      TypeScriptFunction dtsFunction = (TypeScriptFunction) dtsElement;
      return findChildrenOfType(jsFiles, JSFunction.class)
          .stream()
          .filter(f -> staticModifierEquals(f, dtsFunction))
          .collect(Collectors.toList());
    } else if (dtsElement instanceof TypeScriptEnum) {
      return findChildrenOfType(jsFiles, JSObjectLiteralExpression.class)
          .stream()
          .map(PsiElement::getParent)
          .filter(JSAssignmentExpression.class::isInstance)
          .map(PsiElement::getFirstChild)
          .filter(JSDefinitionExpression.class::isInstance)
          .map(JSDefinitionExpression.class::cast)
          .collect(Collectors.toList());
    } else if (dtsElement instanceof TypeScriptEnumField) {
      return findChildrenOfType(jsFiles, JSProperty.class);
    }
    return ImmutableList.of();
  }

  private static final Pattern GENERATED_FROM_JS_COMMENT =
      Pattern.compile("^//!! Processing provides \\[.*] from input (.*\\.js)$");

  private static Collection<JSFile> jsFilesFromDtsFile(
      ExecutionRootPathResolver pathResolver,
      LocalFileSystem lfs,
      PsiManager psiManager,
      JSFile dtsFile) {
    ImmutableList.Builder<JSFile> jsFiles = ImmutableList.builder();
    for (PsiElement child : dtsFile.getChildren()) {
      if (child instanceof PsiWhiteSpace) {
        continue;
      }
      JSFile jsFile =
          Optional.of(child)
              .filter(PsiComment.class::isInstance)
              .map(PsiComment.class::cast)
              .map(PsiComment::getText)
              .map(GENERATED_FROM_JS_COMMENT::matcher)
              .filter(Matcher::find)
              .map(m -> m.group(1))
              .map(ExecutionRootPath::new)
              .map(pathResolver::resolveExecutionRootPath)
              .map(lfs::findFileByIoFile)
              .map(psiManager::findFile)
              .filter(JSFile.class::isInstance)
              .map(JSFile.class::cast)
              .orElse(null);
      if (jsFile != null) {
        jsFiles.add(jsFile);
      } else {
        break;
      }
    }
    return jsFiles.build();
  }

  /**
   * In goog.module()s, the name "exports" replaces the actual exported symbol. E.g.,
   *
   * <pre>
   * goog.module('Foo');
   * exports.bar = null; // assigns to Foo.bar
   * </pre>
   */
  @Nullable
  private static String getJsQualifiedName(JSQualifiedNamedElement jsElement) {
    String exports = "exports.";
    String qualifiedName = jsElement.getQualifiedName();
    if (qualifiedName == null || !qualifiedName.startsWith(exports)) {
      return qualifiedName;
    }
    String exportedName = qualifiedName.substring(exports.length());
    return Stream.of(jsElement)
        .map(PsiElement::getContainingFile)
        .map(JSFile.class::cast)
        .map(ImmutableList::of)
        // should be only one goog.module()
        .map(BlazeTypescriptGotoDeclarationHandler::getModuleDeclarations)
        .flatMap(Collection::stream)
        .findFirst()
        .map(m -> m.getStringValue() + "." + exportedName)
        .orElse(qualifiedName);
  }

  /** Undo a bunch of clutz transformations. https://github.com/angular/clutz */
  @Nullable
  private static String getDtsQualifiedName(JSQualifiedNamedElement dtsElement) {
    String qualifiedName = dtsElement.getQualifiedName();
    if (qualifiedName == null) {
      return null;
    }
    String clutz = "ಠ_ಠ.clutz.";
    String instance = "_Instance.";
    String moduleExports = "module$exports$";
    if (qualifiedName.startsWith(clutz)) {
      qualifiedName = qualifiedName.substring(clutz.length());
    }
    if (qualifiedName.contains(instance)) {
      qualifiedName = qualifiedName.replace(instance, ".");
    }
    if (qualifiedName.startsWith(moduleExports)) {
      qualifiedName = qualifiedName.substring(moduleExports.length());
      qualifiedName = qualifiedName.replace('$', '.');
    }
    return qualifiedName;
  }

  private static String getModuleName(String qualifiedName) {
    String moduleGoog = "module:goog:";
    return qualifiedName.startsWith(moduleGoog)
        ? qualifiedName.substring(moduleGoog.length())
        // for nested namespaces
        : qualifiedName;
  }

  private static Collection<JSLiteralExpression> getModuleDeclarations(Collection<JSFile> jsFiles) {
    return findChildrenOfType(jsFiles, JSCallExpression.class)
        .stream()
        .filter(
            call -> {
              String method = call.getMethodExpression().getText();
              return Objects.equals(method, "goog.provide")
                  || Objects.equals(method, "goog.module");
            })
        .map(JSCallExpression::getArguments)
        .filter(a -> a.length == 1)
        .map(a -> a[0])
        .filter(JSLiteralExpression.class::isInstance)
        .map(JSLiteralExpression.class::cast)
        .filter(JSLiteralExpression::isQuotedLiteral)
        .collect(Collectors.toList());
  }

  private static <T extends PsiElement> Collection<T> findChildrenOfType(
      Collection<JSFile> jsFiles, Class<? extends T> aClass) {
    return jsFiles
        .stream()
        .map(f -> PsiTreeUtil.findChildrenOfType(f, aClass))
        .flatMap(Collection::stream)
        .collect(Collectors.toList());
  }

  private static boolean isConstructorWithName(JSFunction jsFunction, String moduleName) {
    // Prototype-based and ES6 classes will have isConstructor() return true.
    // goog.defineClass() constructors aren't recognized by isConstructor(), so must check name.
    if (!jsFunction.isConstructor() && !Objects.equals(jsFunction.getName(), "constructor")) {
      return false;
    }
    // Prototype-based constructor will have same name as module.
    // ES6 and goog.defineClass() constructors will have their own name.
    String jsQualifiedName = getJsQualifiedName(jsFunction);
    return Objects.equals(jsQualifiedName, moduleName)
        || Objects.equals(jsQualifiedName, moduleName + ".constructor");
  }

  private static boolean staticModifierEquals(
      JSFunction jsFunction, TypeScriptFunction dtsFunction) {
    boolean dtsIsStatic =
        Optional.of(dtsFunction)
            .map(JSAttributeListOwner::getAttributeList)
            .filter(a -> a.hasModifier(ModifierType.STATIC))
            .isPresent();
    return dtsIsStatic == jsIsStatic(jsFunction);
  }

  private static boolean jsIsStatic(JSFunction jsFunction) {
    if (jsFunction.getParent() instanceof JSAssignmentExpression) {
      // pre-ES6 prototype assignment based classes
      // Class.foo = function() {};           <- static
      // Class.prototype.bar = function() {}; <- non-static
      return Optional.of(jsFunction)
          .map(PsiElement::getParent)
          .map(PsiElement::getFirstChild)
          .filter(JSDefinitionExpression.class::isInstance)
          .filter(d -> !d.getText().contains(".prototype."))
          .isPresent();
    } else if (jsFunction.getParent() instanceof JSProperty) {
      // goog.defineClass(..., {
      //   foo: function() {}, <--- JSFunction (non-static)
      //   v----------------------- JSProperty
      //   statics: { <------------ JSObjectLiteralExpression
      //     v--------------------- JSProperty
      //     bar: function() {}, <- JSFunction (static)
      //   },
      // })
      return Optional.of(jsFunction)
          .map(PsiElement::getParent)
          .map(PsiElement::getParent)
          .filter(JSObjectLiteralExpression.class::isInstance)
          .map(PsiElement::getParent)
          .filter(JSProperty.class::isInstance)
          .map(JSProperty.class::cast)
          .filter(p -> Objects.equals(p.getName(), "statics"))
          .isPresent();
    } else if (jsFunction.getParent() instanceof JSClass) {
      // ES6 classes
      return Optional.of(jsFunction)
          .map(JSAttributeListOwner::getAttributeList)
          .filter(a -> a.hasModifier(ModifierType.STATIC))
          .isPresent();
    }
    // Shouldn't happen unless it's a standalone function.
    // Probably makes sense to call it static.
    // It wouldn't match any class-qualified TS function by name anyway.
    return true;
  }

  @Nullable
  @Override
  public String getActionText(DataContext context) {
    return null;
  }
}
