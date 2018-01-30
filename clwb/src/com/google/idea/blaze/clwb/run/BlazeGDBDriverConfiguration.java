/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.clwb.run;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.sdkcompat.clion.BlazeGDBDriverConfigurationBase;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import java.io.File;
import java.io.IOException;
import javax.annotation.Nullable;

final class BlazeGDBDriverConfiguration extends BlazeGDBDriverConfigurationBase {
  private static final Logger LOG = Logger.getInstance(BlazeGDBDriverConfiguration.class);

  private final ImmutableList<String> startupCommands;
  private final WorkspaceRoot workspaceRoot;
  private final Project project;

  BlazeGDBDriverConfiguration(
      Project project, ImmutableList<String> startupCommands, WorkspaceRoot workspaceRoot) {
    super(project);
    this.project = project;
    this.startupCommands = startupCommands;
    this.workspaceRoot = workspaceRoot;
  }

  @Override
  public void modifyCommandLine(GeneralCommandLine commandLine) {
    // Add our GDB commands to run after startup
    for (String command : startupCommands) {
      commandLine.addParameter("-ex");
      commandLine.addParameter(command);
    }
    commandLine.setWorkDirectory(workspaceRoot.directory());
  }

  @Override
  public String convertToLocalPath(@Nullable String absolutePath) {
    if (absolutePath != null) {
      final File file = new File(absolutePath);
      final File workspaceDirectory = workspaceRoot.directory();
      final String relativePath = gdbPathToWorkspaceRelativePath(workspaceDirectory, file);
      File git5SafeFile = null;
      BlazeProjectData blazeProjectData =
          BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
      if (blazeProjectData != null) {
        git5SafeFile = blazeProjectData.workspacePathResolver.resolveToFile(relativePath);
      }
      absolutePath = git5SafeFile == null ? null : git5SafeFile.getPath();
    }
    return super.convertToLocalPath(absolutePath);
  }

  /**
   * Heuristic to try to handle the case where the file returned by gdb uses the canonical path but
   * the user imported their project using a non-canonical path. For example, this handles the case
   * where the user keeps their git5 repos on a different mount and accesses them via a symlink from
   * their home directory.
   *
   * @param workspaceDirectory workspace root, as it was imported into CLion
   * @param file file returned by GDB
   * @return The relative path for {@param file} as it was imported into CLion
   */
  private String gdbPathToWorkspaceRelativePath(File workspaceDirectory, File file) {
    try {
      File canonicalWorkspaceDirectory = workspaceDirectory.getCanonicalFile();
      File canonicalFile = file.getCanonicalFile();
      String relativeCanonicalPath =
          FileUtil.getRelativePath(canonicalWorkspaceDirectory, canonicalFile);
      if (relativeCanonicalPath != null) {
        return relativeCanonicalPath;
      }
    } catch (IOException e) {
      LOG.info(e);
    }
    return file.getPath();
  }
}
