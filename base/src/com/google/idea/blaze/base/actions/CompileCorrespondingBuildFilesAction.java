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
package com.google.idea.blaze.base.actions;

import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.build.BlazeBuildService;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.lang.buildfile.search.BlazePackage;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.sync.status.BlazeSyncStatus;
import com.google.idea.common.actions.ActionPresentationHelper;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

/** Allows a partial sync of the project depending on what's been selected. */
public class CompileCorrespondingBuildFilesAction extends BlazeProjectAction {

  @Override
  protected void actionPerformedInBlazeProject(Project project, AnActionEvent e) {
    if (!BlazeSyncStatus.getInstance(project).syncInProgress()) {
      runBuild(project, e);
    }
    updateStatus(project, e);
  }


  private static void updateStatus(Project project, AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    presentation.setEnabled(!BlazeSyncStatus.getInstance(project).syncInProgress());
  }

  protected void runBuild(Project project, AnActionEvent e) {
    VirtualFile vf = e.getData(CommonDataKeys.VIRTUAL_FILE);
    ImmutableSet<Label> data = getAllTargets(project,vf);
    if (data != null) {
      BlazeBuildService.getInstance(project).buildFile("",data);
    }
  }

  private static ImmutableSet<Label> getAllTargets(Project project, VirtualFile vf){
    ImmutableSet.Builder<Label> builder = ImmutableSet.builder();
    getAllTargetsRecursive(project,vf,builder);
    return builder.build();
  }


  private static void getAllTargetsRecursive(Project project, VirtualFile vf, ImmutableSet.Builder<Label> targetsBuilder){
    if(!vf.isDirectory()){
      BlazePackage parentPackage = BuildFileUtils.getBuildFile(project, vf);
      if (parentPackage != null) {
        targetsBuilder.add(getLabelForBuildFile(parentPackage.buildFile));
      }
      return;
    }
    for(VirtualFile innerVf : vf.getChildren()){
      getAllTargetsRecursive(project,innerVf,targetsBuilder);
    }
  }

  private static Label getLabelForBuildFile(BuildFile buildFile){
    return Label.create("//" + buildFile.getBlazePackage().getPackageLabel().blazePackage() + ":all");
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
        .disableIf(BlazeSyncStatus.getInstance(project).syncInProgress())
        .setText("Compile corresponding build files")
        .hideInContextMenuIfDisabled()
        .commit();
  }

  @Override
  protected QuerySyncStatus querySyncSupport() {
    return QuerySyncStatus.SUPPORTED;
  }
}
