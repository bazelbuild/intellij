/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.actions;

import com.google.idea.blaze.base.build.BlazeBuildService;
import com.google.idea.blaze.base.model.primitives.*;
import com.google.idea.blaze.base.lang.buildfile.search.BlazePackage;
import com.google.idea.common.actions.ActionPresentationHelper;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.File;
import java.util.List;

/** Action to compile all build files in folder if folder picked or compile corresponding build file if file picked */
public class CompileFolderAction extends BlazeProjectAction {

  @Override
  protected void actionPerformedInBlazeProject(Project project, AnActionEvent e) {
      runBuild(project, e);
  }

  protected void runBuild(Project project, AnActionEvent e) {
    VirtualFile vf = e.getData(CommonDataKeys.VIRTUAL_FILE);
    WorkspaceRoot root = WorkspaceRoot.fromProject(project);
    WorkspacePath path = root.workspacePathForSafe(new File(vf.getPath()));
    try {
      List<TargetExpression> dt = List.of(TargetExpression.fromString("//" + path.toString() + "/..."));
      BlazeBuildService.getInstance(project).buildFolder(vf.getName(),dt);
    } catch (InvalidTargetException ex) {
      throw new RuntimeException(ex);
    }
  }

  @Override
  protected void updateForBlazeProject(Project project, AnActionEvent e) {
    Presentation presentation = e.getPresentation();

    DataContext dataContext = e.getDataContext();
    VirtualFile virtualFile = CommonDataKeys.VIRTUAL_FILE.getData(dataContext);
    BlazePackage blazePackage = BuildFileUtils.getBuildFile(project, virtualFile);

    boolean enabled = blazePackage != null;
    presentation.setVisible(enabled || !ActionPlaces.isPopupPlace(e.getPlace()));
    presentation.setEnabled(enabled);

    ActionPresentationHelper.of(e)
            .disableIf(virtualFile == null)
            .disableIf(!virtualFile.isDirectory())
            .setText("Compile " + virtualFile.getName() + "/...:all")
            .hideInContextMenuIfDisabled()
            .commit();
  }

  @Override
  protected QuerySyncStatus querySyncSupport() {
    return QuerySyncStatus.SUPPORTED;
  }
}
