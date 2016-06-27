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
import com.google.idea.blaze.base.actions.BlazeAction;
import com.google.idea.blaze.base.metrics.Action;
import com.google.idea.blaze.base.metrics.LoggingService;
import com.intellij.ide.actions.OpenFileAction;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;

public class OpenCorrespondingBuildFile extends BlazeAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) {
      return;
    }
    VirtualFile virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE);
    File file = getBuildFile(project, virtualFile);
    if (file == null) {
      return;
    }
    OpenFileAction.openFile(file.getPath(), project);
    LoggingService.reportEvent(project, Action.OPEN_CORRESPONDING_BUILD_FILE);
  }

  @Nullable
  private File getBuildFile(@Nullable Project project, @Nullable VirtualFile virtualFile) {
    if (project == null) {
      return null;
    }
    if (virtualFile == null) {
      return null;
    }
    File file = new File(virtualFile.getPath());
    Collection<File> fileInfoList = FileToBuildMap.getInstance(project).getBuildFilesForFile(file);
    return Iterables.getFirst(fileInfoList, null);
  }

  @Override
  protected void doUpdate(@NotNull AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    DataContext dataContext = e.getDataContext();
    VirtualFile virtualFile = CommonDataKeys.VIRTUAL_FILE.getData(dataContext);
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    boolean visible = (project != null && virtualFile != null);
    boolean enabled = getBuildFile(project, virtualFile) != null;
    presentation.setVisible(visible || ActionPlaces.isMainMenuOrActionSearch(e.getPlace()));
    presentation.setEnabled(enabled);
  }
}
