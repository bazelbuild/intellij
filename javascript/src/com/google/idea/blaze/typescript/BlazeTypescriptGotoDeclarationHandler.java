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
package com.google.idea.blaze.typescript;

import static com.google.common.collect.ImmutableList.toImmutableList;

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
import com.intellij.lang.javascript.dialects.TypeScriptLanguageDialect;
import com.intellij.lang.javascript.ecmascript6.TypeScriptUtil;
import com.intellij.lang.javascript.psi.JSAssignmentExpression;
import com.intellij.lang.javascript.psi.JSCallExpression;
import com.intellij.lang.javascript.psi.JSDefinitionExpression;
import com.intellij.lang.javascript.psi.JSExpression;
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
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
        || !isJsIdentifier(sourceElement)
        || !(sourceElement.getContainingFile().getLanguage()
            instanceof TypeScriptLanguageDialect)) {
      return null;
    }
    Project project = sourceElement.getProject();
    BlazeProjectData projectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (!Blaze.isBlazeProject(project)
        || projectData == null
        || !projectData.getWorkspaceLanguageSettings().isLanguageActive(LanguageClass.JAVASCRIPT)
        || !projectData.getWorkspaceLanguageSettings().isLanguageActive(LanguageClass.TYPESCRIPT)) {
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
    return resolvedToDts.stream()
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

  private static ImmutableList<PsiElement> resolveToDts(JSReferenceExpression referenceExpression) {
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
        .collect(toImmutableList());
  }

  private static ImmutableList<PsiElement> resolveToJs(
      ExecutionRootPathResolver pathResolver,
      LocalFileSystem lfs,
      PsiManager psiManager,
      boolean isConstructor,
      PsiElement dtsElement) {
    dtsElement = PsiTreeUtil.getParentOfType(dtsElement, JSQualifiedNamedElement.class, false);
    if (dtsElement == null) {
      return ImmutableList.of();
    }
    if (!TypeScriptUtil.isDefinitionFile(dtsElement.getContainingFile())) {
      return ImmutableList.of();
    }
    String qualifiedName = getDtsQualifiedName((JSQualifiedNamedElement) dtsElement);
    if (qualifiedName == null) {
      return ImmutableList.of();
    }
    ImmutableList<JSFile> jsFiles = jsFilesFromDtsSymbol(pathResolver, lfs, psiManager, dtsElement);
    if (jsFiles.isEmpty()) {
      return ImmutableList.of();
    }
    if (dtsElement instanceof TypeScriptModule) {
      String moduleName = getModuleName(qualifiedName);
      return isConstructor
          ? findChildrenOfType(jsFiles, JSFunction.class).stream()
              .filter(e -> isConstructorWithName(e, moduleName))
              .collect(toImmutableList())
          : getModuleDeclarations(jsFiles).stream()
              .filter(a -> Objects.equals(a.getStringValue(), moduleName))
              .collect(toImmutableList());
    }
    return getResolveCandidates(dtsElement, jsFiles).stream()
        .filter(e -> Objects.equals(getJsQualifiedName(e), qualifiedName))
        .collect(toImmutableList());
  }

  private static ImmutableList<? extends JSQualifiedNamedElement> getResolveCandidates(
      PsiElement dtsElement, ImmutableList<JSFile> jsFiles) {
    if (dtsElement instanceof TypeScriptClass) {
      return Stream.concat(
              findChildrenOfType(jsFiles, JSClass.class).stream(),
              // Apparently you can declare a JS class with just a constructor function and
              // attach some properties to it.
              findChildrenOfType(jsFiles, JSFunction.class).stream())
          .collect(toImmutableList());
    } else if (dtsElement instanceof TypeScriptFunction) {
      TypeScriptFunction dtsFunction = (TypeScriptFunction) dtsElement;
      return findChildrenOfType(jsFiles, JSFunction.class).stream()
          .filter(f -> staticModifierEquals(f, dtsFunction))
          .collect(toImmutableList());
    } else if (dtsElement instanceof TypeScriptEnum) {
      return findChildrenOfType(jsFiles, JSObjectLiteralExpression.class).stream()
          .map(PsiElement::getParent)
          .filter(JSAssignmentExpression.class::isInstance)
          .map(PsiElement::getFirstChild)
          .filter(JSDefinitionExpression.class::isInstance)
          .map(JSDefinitionExpression.class::cast)
          .collect(toImmutableList());
    } else if (dtsElement instanceof TypeScriptEnumField) {
      return findChildrenOfType(jsFiles, JSProperty.class);
    }
    return ImmutableList.of();
  }

  /**
   * Comment above each symbol declaring their source .js file. E.g.,
   *
   * <pre>// Generated from foo.bar.js</pre>
   *
   * This is necessary after the change to split .jspb.js file into multiple separate files. Each
   * symbol within the same .d.ts file could come from different .jspb.js files.
   */
  private static final Pattern SYMBOL_GENERATED_FROM_JS_COMMENT =
      Pattern.compile("^// Generated from (.*\\.js)$");

  private static ImmutableList<JSFile> jsFilesFromDtsSymbol(
      ExecutionRootPathResolver pathResolver,
      LocalFileSystem lfs,
      PsiManager psiManager,
      PsiElement dtsElement) {
    while (dtsElement != null && !(dtsElement instanceof PsiFile)) {
      PsiElement comment =
          PsiTreeUtil.findSiblingBackward(dtsElement, JSTokenTypes.END_OF_LINE_COMMENT, null);
      if (comment != null) {
        Matcher matcher = SYMBOL_GENERATED_FROM_JS_COMMENT.matcher(comment.getText());
        if (matcher.find()) {
          JSFile file = pathToJsFile(pathResolver, lfs, psiManager, matcher.group(1));
          return file != null ? ImmutableList.of(file) : ImmutableList.of();
        }
      }
      dtsElement = dtsElement.getParent();
    }
    return ImmutableList.of();
  }

  @Nullable
  private static JSFile pathToJsFile(
      ExecutionRootPathResolver pathResolver,
      LocalFileSystem lfs,
      PsiManager psiManager,
      String path) {
    return Optional.of(path)
        .map(ExecutionRootPath::new)
        .map(pathResolver::resolveExecutionRootPath)
        .map(lfs::findFileByIoFile)
        .map(psiManager::findFile)
        .filter(JSFile.class::isInstance)
        .map(JSFile.class::cast)
        .orElse(null);
  }

  /**
   * Usually, we can just compare {@link JSQualifiedNamedElement#getQualifiedName()}, but in
   * goog.module()s, the name "exports" replaces the actual exported symbol. E.g.,
   *
   * <pre>
   * goog.module('Foo');
   * exports.bar = goog.defineClass(null, { foo: function() {}});
   * </pre>
   *
   * creates a function with the qualified name of Foo.bar.foo.
   */
  @Nullable
  private static String getJsQualifiedName(JSQualifiedNamedElement jsElement) {
    String exportedName =
        Optional.ofNullable(
                PsiTreeUtil.getTopmostParentOfType(jsElement, JSAssignmentExpression.class))
            .map(JSAssignmentExpression::getDefinitionExpression)
            .map(JSQualifiedNamedElement::getQualifiedName)
            .filter(name -> name.equals("exports") || name.startsWith("exports."))
            .orElse(null);
    String qualifiedName = jsElement.getQualifiedName();
    if (qualifiedName == null || exportedName == null) {
      return qualifiedName;
    }
    String moduleName =
        Stream.of(jsElement)
            .map(PsiElement::getContainingFile)
            .map(JSFile.class::cast)
            .map(ImmutableList::of)
            // should be only one goog.module()
            .map(BlazeTypescriptGotoDeclarationHandler::getModuleDeclarations)
            .flatMap(Collection::stream)
            .findFirst()
            .map(JSLiteralExpression::getStringValue)
            .orElse(null);
    // if exports is already part of the element's qualified name, then the exported name is already
    // included, otherwise we have to include the exported name
    return qualifiedName.startsWith("exports")
        ? moduleName + qualifiedName.substring("exports".length())
        : moduleName + exportedName.substring("exports".length()) + '.' + qualifiedName;
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
    if (qualifiedName.startsWith("module:")) {
      qualifiedName = qualifiedName.substring("module:".length());
    }
    if (qualifiedName.startsWith("\"")
        && qualifiedName.length() >= 2
        && qualifiedName.endsWith("\"")) {
      qualifiedName = qualifiedName.substring(1, qualifiedName.length() - 1);
    }
    if (qualifiedName.startsWith("goog:")) {
      qualifiedName = qualifiedName.substring("goog:".length());
    }
    return qualifiedName;
  }

  private static ImmutableList<JSLiteralExpression> getModuleDeclarations(
      ImmutableList<JSFile> jsFiles) {
    return findChildrenOfType(jsFiles, JSCallExpression.class).stream()
        .filter(
            call -> {
              JSExpression method = call.getMethodExpression();
              return method != null
                  && (Objects.equals(method.getText(), "goog.provide")
                      || Objects.equals(method.getText(), "goog.module"));
            })
        .map(JSCallExpression::getArguments)
        .filter(a -> a.length == 1)
        .map(a -> a[0])
        .filter(JSLiteralExpression.class::isInstance)
        .map(JSLiteralExpression.class::cast)
        .filter(JSLiteralExpression::isQuotedLiteral)
        .collect(toImmutableList());
  }

  private static <T extends PsiElement> ImmutableList<T> findChildrenOfType(
      ImmutableList<JSFile> jsFiles, Class<? extends T> aClass) {
    return jsFiles.stream()
        .map(f -> PsiTreeUtil.findChildrenOfType(f, aClass))
        .flatMap(Collection::stream)
        .collect(toImmutableList());
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
