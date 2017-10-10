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
package com.google.idea.blaze.base.buildmap;

import com.google.idea.blaze.base.actions.BlazeProjectAction;
import com.google.idea.blaze.base.lang.buildfile.references.BuildReferenceManager;
import com.google.idea.blaze.base.lang.buildfile.search.BlazePackage;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.targetmaps.SourceToTargetMap;
import com.intellij.ide.actions.OpenFileAction;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.NavigatablePsiElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiManager;
import java.io.File;
import javax.annotation.Nullable;

class OpenCorrespondingBuildFile extends BlazeProjectAction {

  @Override
  protected void actionPerformedInBlazeProject(Project project, AnActionEvent e) {
    VirtualFile vf = e.getData(CommonDataKeys.VIRTUAL_FILE);
    if (vf == null) {
      return;
    }
    navigateToTargetOrFile(project, vf);
  }

  /** Returns true if a target or BUILD file could be found and navigated to. */
  private static void navigateToTargetOrFile(Project project, VirtualFile vf) {
    // First, find the parent BUILD file. We don't want to navigate to labels in other packages
    BlazePackage parentPackage = getBuildFile(project, vf);
    if (parentPackage == null) {
      return;
    }
    // first, look for a specific target which includes this source file
    PsiElement target = findBuildTarget(project, parentPackage, new File(vf.getPath()));
    if (target instanceof NavigatablePsiElement) {
      ((NavigatablePsiElement) target).navigate(true);
      return;
    }
    OpenFileAction.openFile(parentPackage.buildFile.getFile().getPath(), project);
  }

  @Nullable
  private static BlazePackage getBuildFile(Project project, @Nullable VirtualFile vf) {
    if (vf == null) {
      return null;
    }
    PsiManager manager = PsiManager.getInstance(project);
    PsiFileSystemItem psiFile = vf.isDirectory() ? manager.findDirectory(vf) : manager.findFile(vf);
    if (psiFile == null) {
      return null;
    }
    return BlazePackage.getContainingPackage(psiFile);
  }

  @Nullable
  private static PsiElement findBuildTarget(
      Project project, BlazePackage parentPackage, File file) {
    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (blazeProjectData == null) {
      return null;
    }
    File parentFile = parentPackage.buildFile.getFile().getParentFile();
    WorkspacePath packagePath =
        parentFile != null
            ? blazeProjectData.workspacePathResolver.getWorkspacePath(parentFile)
            : null;
    if (packagePath == null) {
      return null;
    }
    Label label =
        SourceToTargetMap.getInstance(project)
            .getTargetsToBuildForSourceFile(file)
            .stream()
            .filter(l -> l.blazePackage().equals(packagePath))
            .findFirst()
            .orElse(null);
    if (label == null) {
      return null;
    }
    return BuildReferenceManager.getInstance(project).resolveLabel(label);
  }

  @Override
  protected void updateForBlazeProject(Project project, AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    DataContext dataContext = e.getDataContext();
    VirtualFile virtualFile = CommonDataKeys.VIRTUAL_FILE.getData(dataContext);
    boolean visible = virtualFile != null;
    boolean enabled = getBuildFile(project, virtualFile) != null;
    presentation.setVisible(visible || ActionPlaces.isMainMenuOrActionSearch(e.getPlace()));
    presentation.setEnabled(enabled);
  }
}
