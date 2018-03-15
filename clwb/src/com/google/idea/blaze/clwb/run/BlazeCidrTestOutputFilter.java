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
package com.google.idea.blaze.clwb.run;

import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.filters.RegexpFilter;
import com.intellij.openapi.project.Project;
import java.io.File;
import javax.annotation.Nullable;

/** Identifies file paths in CLion test output which aren't found by CidrPathConsoleFilter. */
public class BlazeCidrTestOutputFilter extends RegexpFilter {

  private static final String FAILURE_REGEX =
      String.format("%s:%s: Failure", RegexpFilter.FILE_PATH_MACROS, RegexpFilter.LINE_MACROS);

  @Nullable private final WorkspacePathResolver workspacePathResolver;

  public BlazeCidrTestOutputFilter(Project project) {
    super(project, FAILURE_REGEX);
    BlazeProjectData data = BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    workspacePathResolver = data != null ? data.workspacePathResolver : null;
  }

  @Nullable
  @Override
  protected HyperlinkInfo createOpenFileHyperlink(String fileName, int line, int column) {
    HyperlinkInfo result = super.createOpenFileHyperlink(fileName, line, column);
    if (result != null || workspacePathResolver == null) {
      return result;
    }
    File workspaceFile = workspacePathResolver.resolveToFile(fileName);
    return super.createOpenFileHyperlink(workspaceFile.getPath(), line, column);
  }
}
