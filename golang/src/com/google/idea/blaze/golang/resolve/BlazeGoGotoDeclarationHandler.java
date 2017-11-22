/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.golang.resolve;

import com.goide.psi.GoFile;
import com.goide.psi.GoImportSpec;
import com.google.common.base.Strings;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.io.FileOperationProvider;
import com.google.idea.blaze.base.io.VirtualFileSystemProvider;
import com.google.idea.blaze.base.lang.buildfile.references.BuildReferenceManager;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.workspace.WorkspaceHelper;
import com.google.idea.blaze.golang.BlazeGoSupport;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.PathUtil;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Handles {@link GotoDeclarationAction} from {@link GoFile}s.
 *
 * <ul>
 *   <li>Import paths will be redirected to the corresponding workspace path (or the rule/BUILD file
 *       if it doesn't exist).
 *   <li>Symbols located in the directory of symlinks, will be replaced with the same symbol after
 *       resolving the symlink.
 * </ul>
 */
public class BlazeGoGotoDeclarationHandler implements GotoDeclarationHandler {
  private static final Logger logger = Logger.getInstance(BlazeGoGotoDeclarationHandler.class);

  @Nullable
  @Override
  public PsiElement[] getGotoDeclarationTargets(
      @Nullable PsiElement sourceElement, int offset, Editor editor) {
    if (sourceElement == null) {
      return null;
    }
    if (!Blaze.isBlazeProject(sourceElement.getProject())
        || !BlazeGoSupport.blazeGoSupportEnabled.getValue()) {
      return null;
    }
    PsiFile sourcefile = sourceElement.getContainingFile();
    if (!(sourcefile instanceof GoFile)) {
      return null;
    }
    PsiElement targetElement =
        TargetElementUtil.getInstance()
            .findTargetElement(editor, TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED, offset);
    if (targetElement instanceof PsiDirectory) {
      return resolveDirectory(sourceElement, (PsiDirectory) targetElement);
    } else {
      return resolveElement(targetElement);
    }
  }

  @Nullable
  private static PsiElement[] resolveElement(@Nullable PsiElement element) {
    if (element == null) {
      return null;
    }
    PsiFile targetPsiFile = element.getContainingFile();
    if (!(targetPsiFile instanceof GoFile)) {
      return null;
    }
    LocalFileSystem lfs = VirtualFileSystemProvider.getInstance().getSystem();
    FileOperationProvider provider = FileOperationProvider.getInstance();
    VirtualFile targetVirtualFile = targetPsiFile.getVirtualFile();
    File targetFile = VfsUtil.virtualToIoFile(targetVirtualFile);
    if (!provider.isSymbolicLink(targetFile)) {
      return null;
    }
    VirtualFile resolved;
    try {
      // Resolve only one layer of symlink.
      File resolvedFile = provider.readSymbolicLink(targetFile);
      resolved = lfs.findFileByIoFile(resolvedFile);
    } catch (IOException e) {
      logger.error(e);
      return null;
    }
    if (resolved == null) {
      return null;
    }
    PsiFile resolvedFile = PsiManager.getInstance(element.getProject()).findFile(resolved);
    if (!(resolvedFile instanceof GoFile)) {
      return null;
    }
    PsiElement foundElement = resolvedFile.findElementAt(element.getTextOffset());
    return new PsiElement[] {PsiTreeUtil.getParentOfType(foundElement, element.getClass())};
  }

  private static PsiElement[] resolveDirectory(PsiElement sourceElement, PsiDirectory directory) {
    GoImportSpec importSpec = PsiTreeUtil.getParentOfType(sourceElement, GoImportSpec.class);
    if (importSpec == null) {
      return null;
    }
    PsiElement resolvedImportPath =
        resolveImportPath(sourceElement.getProject(), importSpec.getPath());
    if (resolvedImportPath == null) {
      return null;
    }
    PsiDirectory goPackage = importSpec.resolve();
    if (goPackage == null) {
      return new PsiElement[] {resolvedImportPath};
    }
    String suffix =
        VfsUtil.getRelativePath(goPackage.getVirtualFile(), directory.getVirtualFile().getParent());
    return new PsiElement[] {removeImportPathSuffix(resolvedImportPath, suffix)};
  }

  @Nullable
  private static PsiElement resolveImportPath(Project project, String importPath) {
    BlazeProjectData projectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (projectData == null) {
      return null;
    }
    Map<String, TargetKey> packageMap = BlazeGoRootsProvider.getPackageToTargetMap(project);
    if (packageMap == null) {
      return null;
    }
    TargetKey targetKey = packageMap.get(importPath);
    if (targetKey == null) {
      return null;
    }
    BuildReferenceManager buildReferenceManager = BuildReferenceManager.getInstance(project);
    PsiElement resolvedLabel = buildReferenceManager.resolveLabel(targetKey.label);
    if (resolvedLabel != null) {
      return resolvedLabel;
    }
    File blazePackage = WorkspaceHelper.resolveBlazePackage(project, targetKey.label);
    return buildReferenceManager.findBuildFile(blazePackage);
  }

  private static PsiElement removeImportPathSuffix(PsiElement resolvedImportPath, String suffix) {
    if (Strings.isNullOrEmpty(suffix)) {
      return resolvedImportPath;
    }
    // Last component is rule name.
    suffix = PathUtil.getParentPath(suffix);
    PsiDirectory path = resolvedImportPath.getContainingFile().getContainingDirectory();
    while (!suffix.isEmpty()
        && path != null
        && PathUtil.getFileName(suffix).equals(path.getName())) {
      suffix = PathUtil.getParentPath(suffix);
      if (suffix.isEmpty()) {
        return path;
      }
      path = path.getParent();
    }
    return resolvedImportPath;
  }

  @Nullable
  @Override
  public String getActionText(DataContext context) {
    return null;
  }
}
