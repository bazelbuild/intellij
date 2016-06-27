/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.lang.buildfile.psi;

import com.google.common.collect.Lists;
import com.google.idea.blaze.base.lang.buildfile.language.BuildFileType;
import com.google.idea.blaze.base.lang.buildfile.references.BuildReferenceManager;
import com.google.idea.blaze.base.lang.buildfile.search.BlazePackage;
import com.google.idea.blaze.base.lang.buildfile.search.ResolveUtil;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.PathUtil;
import com.intellij.util.Processor;
import icons.BlazeIcons;

import javax.annotation.Nullable;
import javax.swing.*;
import java.io.File;
import java.util.List;

/**
 * Build file PSI element
 */
public class BuildFile extends PsiFileBase implements BuildElement {

  public enum BlazeFileType {
    SkylarkExtension,
    BuildPackage // "BUILD", plus hacks such as "BUILD.tools", "BUILD.bazel"
  }

  @Nullable
  public static WorkspacePath getWorkspacePath(Project project, String filePath) {
    return BuildReferenceManager.getInstance(project).getWorkspaceRelativePath(filePath);
  }

  public static String getBuildFileString(Project project, String filePath) {
    WorkspacePath workspacePath = getWorkspacePath(project, PathUtil.getParentPath(filePath));
    if (workspacePath == null) {
      return "BUILD file: " + filePath;
    }
    String fileName = PathUtil.getFileName(filePath);
    if (fileName.startsWith("BUILD")) {
      return "//" + workspacePath + "/" + fileName;
    }
    return "//" + workspacePath + ":" + fileName;
  }

  public BuildFile(FileViewProvider viewProvider) {
    super(viewProvider, BuildFileType.INSTANCE.getLanguage());
  }

  @Override
  public FileType getFileType() {
    return BuildFileType.INSTANCE;
  }

  public BlazeFileType getBlazeFileType() {
    String fileName = getFileName();
    if (fileName.startsWith("BUILD")) {
      return BlazeFileType.BuildPackage;
    }
    return BlazeFileType.SkylarkExtension;
  }

  @Nullable
  @Override
  public BlazePackage getBlazePackage() {
    return BlazePackage.getContainingPackage(this);
  }

  public String getFileName() {
    return getViewProvider().getVirtualFile().getName();
  }

  public String getFilePath() {
    return getOriginalFile().getViewProvider().getVirtualFile().getPath();
  }

  public File getFile() {
    return new File(getFilePath());
  }

  @Nullable
  @Override
  public WorkspacePath getWorkspacePath() {
    return getWorkspacePath(getProject(), getFilePath());
  }

  /**
   * The workspace path of the containing blaze package
   * (this is always the parent directory for BUILD files, but may be a more distant ancestor for Skylark extensions)
   */
  @Nullable
  public WorkspacePath getPackageWorkspacePath() {
    BlazePackage parentPackage = getBlazePackage();
    if (parentPackage == null) {
      return null;
    }
    String filePath = parentPackage.buildFile.getFilePath();
    return filePath != null ? getWorkspacePath(getProject(), PathUtil.getParentPath(filePath)) : null;
  }

  @Nullable
  public String getWorkspaceRelativePackagePath() {
    WorkspacePath packagePath = getPackageWorkspacePath();
    return packagePath != null ? packagePath.relativePath() : null;
  }

  /**
   * Finds a top-level rule with a "name" keyword argument with the given value.
   */
  @Nullable
  public FuncallExpression findRule(String name) {
    for (FuncallExpression expr : findChildrenByClass(FuncallExpression.class)) {
      String ruleName = expr.getNameArgumentValue();
      if (name.equals(ruleName)) {
        return expr;
      }
    }
    return null;
  }

  /**
   * .bzl files referenced in 'load' statements
   */
  @Nullable
  public String[] getImportedPaths() {
    ASTNode[] loadStatements = getNode().getChildren(TokenSet.create(BuildElementTypes.LOAD_STATEMENT));
    if (loadStatements.length == 0) {
      return null;
    }
    List<String> importedPaths = Lists.newArrayListWithCapacity(loadStatements.length);
    for (int i = 0; i < loadStatements.length; i++) {
      String path = ((LoadStatement) loadStatements[i].getPsi()).getImportedPath();
      if (path != null) {
        importedPaths.add(path);
      }
    }
    return importedPaths.toArray(new String[importedPaths.size()]);
  }

  @Nullable
  public FunctionStatement findDeclaredFunction(String name) {
    for (FunctionStatement fn : getFunctionDeclarations()) {
      if (name.equals(fn.getName())) {
        return fn;
      }
    }
    return null;
  }

  @Nullable
  public TargetExpression findTopLevelVariable(String name) {
    return ResolveUtil.searchChildAssignmentStatements(this, name);
  }

  @Nullable
  public FunctionStatement findLoadedFunction(String name) {
    for (LoadStatement loadStatement : findChildrenByClass(LoadStatement.class)) {
      for (StringLiteral importedFunctionNode : loadStatement.getImportedSymbolElements()) {
        if (name.equals(importedFunctionNode.getStringContents())) {
          PsiElement element = importedFunctionNode.getReferencedElement();
          return element instanceof FunctionStatement ? (FunctionStatement) element : null;
        }
      }
    }
    return null;
  }

  public BuildElement findSymbolInScope(String name) {
    BuildElement[] resultHolder = new BuildElement[1];
    Processor<BuildElement> processor = buildElement -> {
      if (buildElement instanceof StringLiteral) {
        buildElement = BuildElement.asBuildElement(buildElement.getReferencedElement());
      }
      if (buildElement instanceof PsiNamedElement
          && name.equals(buildElement.getName())) {
        resultHolder[0] = buildElement;
        return false;
      }
      return true;
    };
    searchSymbolsInScope(processor, null);
    return resultHolder[0];
  }

  /**
   * Iterates over all top-level assignment statements, function definitions and loaded symbols.
   * @return false if searching was stopped (e.g. because the desired element was found).
   */
  public boolean searchSymbolsInScope(Processor<BuildElement> processor, @Nullable PsiElement stopAtElement) {
    for (BuildElement child : findChildrenByClass(BuildElement.class)) {
      if (child == stopAtElement) {
        return true;
      }
      if (child instanceof AssignmentStatement) {
        TargetExpression target = ((AssignmentStatement) child).getLeftHandSideExpression();
        if (target != null && !processor.process(target)) {
          return false;
        }
      } else if (child instanceof FunctionStatement) {
        if (!processor.process(child)) {
          return false;
        }
      } else if (child instanceof LoadStatement) {
        for (StringLiteral importedSymbol : ((LoadStatement) child).getImportedSymbolElements()) {
          if (!processor.process(importedSymbol)) {
            return false;
          }
        }
      }
    }
    return true;
  }

  /**
   * Searches functions declared in this file, then loaded Skylark extensions, if relevant.
   */
  @Nullable
  public FunctionStatement findFunctionInScope(String name) {
    FunctionStatement localFn = findDeclaredFunction(name);
    if (localFn != null) {
      return localFn;
    }
    return findLoadedFunction(name);
  }

  public FunctionStatement[] getFunctionDeclarations() {
    return findChildrenByClass(FunctionStatement.class);
  }

  @Override
  public Icon getIcon(int flags) {
    return BlazeIcons.BuildFile;
  }

  @Override
  public String getPresentableText() {
    return toString();
  }

  @Override
  public String toString() {
    return getBuildFileString(getProject(), getFilePath());
  }

  @Nullable
  @Override
  public PsiElement getReferencedElement() {
    return null;
  }

  @Override
  public <P extends PsiElement> P[] childrenOfClass(Class<P> psiClass) {
    return findChildrenByClass(psiClass);
  }

  @Override
  public <P extends PsiElement> P firstChildOfClass(Class<P> psiClass) {
    return findChildByClass(psiClass);
  }
}

