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

import com.google.common.collect.Iterables;
import com.google.idea.blaze.base.actions.BlazeProjectAction;
import com.google.idea.blaze.base.metrics.Action;
import com.google.idea.blaze.base.metrics.LoggingService;
import com.intellij.ide.actions.OpenFileAction;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.File;
import java.util.Collection;
import javax.annotation.Nullable;

class OpenCorrespondingBuildFile extends BlazeProjectAction {

  @Override
  protected void actionPerformedInBlazeProject(Project project, AnActionEvent e) {
    VirtualFile virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE);
    File file = getBuildFile(project, virtualFile);
    if (file == null) {
      return;
    }
    OpenFileAction.openFile(file.getPath(), project);
    LoggingService.reportEvent(project, Action.OPEN_CORRESPONDING_BUILD_FILE);
  }

  @Nullable
  private File getBuildFile(Project project, @Nullable VirtualFile virtualFile) {
    if (virtualFile == null) {
      return null;
    }
    File file = new File(virtualFile.getPath());
    Collection<File> fileInfoList = FileToBuildMap.getInstance(project).getBuildFilesForFile(file);
    return Iterables.getFirst(fileInfoList, null);
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
