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
package com.google.idea.blaze.golang.resolve;

import com.goide.psi.GoFile;
import com.goide.psi.GoResolvable;
import com.google.idea.blaze.base.io.FileOperationProvider;
import com.google.idea.blaze.base.io.VirtualFileSystemProvider;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.golang.BlazeGoSupport;
import com.google.idea.blaze.golang.sync.BlazeGoLibrary;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import java.io.File;
import java.io.IOException;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * Handles {@link GotoDeclarationAction} from {@link GoFile}s in blaze_go_library.
 *
 * <p>Resolves to the same symbol after resolving the symlink.
 */
public class BlazeGoLibrarySymlinkGotoDeclarationHandler implements GotoDeclarationHandler {
  private static final Logger logger =
      Logger.getInstance(BlazeGoLibrarySymlinkGotoDeclarationHandler.class);

  @Nullable
  @Override
  public PsiElement[] getGotoDeclarationTargets(
      @Nullable PsiElement sourceElement, int offset, Editor editor) {
    return Optional.ofNullable(sourceElement)
        .filter(e -> Blaze.isBlazeProject(e.getProject()))
        .filter(e -> BlazeGoSupport.blazeGoSupportEnabled.getValue())
        .filter(e -> BlazeGoLibrary.useGoLibrary.getValue())
        .filter(e -> e.getContainingFile() instanceof GoFile)
        .map(e -> PsiTreeUtil.getParentOfType(e, GoResolvable.class))
        .map(GoResolvable::resolve)
        .map(BlazeGoLibrarySymlinkGotoDeclarationHandler::resolveSymlink)
        .orElse(null);
  }

  @Nullable
  private static PsiElement[] resolveSymlink(PsiElement element) {
    File goLibrary = BlazeGoLibrary.getLibraryRoot(element.getProject());
    if (goLibrary == null || !goLibrary.exists()) {
      return null;
    }
    PsiFile targetPsiFile = element.getContainingFile();
    if (!(targetPsiFile instanceof GoFile)) {
      return null;
    }
    VirtualFile targetVirtualFile = targetPsiFile.getVirtualFile();
    File targetFile = VfsUtil.virtualToIoFile(targetVirtualFile);
    if (!FileUtil.isAncestor(goLibrary, targetFile, true)) {
      return null;
    }
    FileOperationProvider fileOperations = FileOperationProvider.getInstance();
    if (!fileOperations.isSymbolicLink(targetFile)) {
      return null;
    }
    LocalFileSystem lfs = VirtualFileSystemProvider.getInstance().getSystem();
    VirtualFile resolved;
    try {
      // Resolve only one layer of symlink.
      File resolvedFile = fileOperations.readSymbolicLink(targetFile);
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
    foundElement = PsiTreeUtil.getParentOfType(foundElement, element.getClass(), false);
    return foundElement != null ? new PsiElement[] {foundElement} : null;
  }

  @Nullable
  @Override
  public String getActionText(DataContext context) {
    return null;
  }
}
