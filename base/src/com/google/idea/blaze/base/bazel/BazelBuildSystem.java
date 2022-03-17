/*
 * Copyright 2022 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.bazel;

import com.google.errorprone.annotations.MustBeClosed;
import com.google.idea.blaze.base.command.BlazeCommandRunner;
import com.google.idea.blaze.base.command.CommandLineBlazeCommandRunner;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelperBep;
import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.section.sections.BazelBinarySection;
import com.google.idea.blaze.base.settings.BlazeUserSettings;
import com.google.idea.blaze.base.settings.BuildBinaryType;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.util.Optional;
import javax.annotation.Nullable;

class BazelBuildSystem implements BuildSystem {

  static class BazelInvoker implements BuildInvoker {
    private final String path;
    private final BlazeCommandRunner runner = new CommandLineBlazeCommandRunner();

    public BazelInvoker(String path) {
      this.path = path;
    }

    @Override
    public BuildBinaryType getType() {
      return BuildBinaryType.BAZEL;
    }

    @Override
    public String getBinaryPath() {
      return path;
    }

    @Override
    public boolean supportsParallelism() {
      return false;
    }

    @Override
    @MustBeClosed
    public BuildResultHelper createBuildResultProvider() {
      return new BuildResultHelperBep();
    }

    @Override
    public BlazeCommandRunner getCommandRunner() {
      return runner;
    }
  }

  @Override
  public BuildSystemName getName() {
    return BuildSystemName.Bazel;
  }

  @Override
  public BuildInvoker getBuildInvoker(Project project) {
    String binaryPath;
    File projectSpecificBinary = getProjectSpecificBazelBinary(project);
    if (projectSpecificBinary != null) {
      binaryPath = projectSpecificBinary.getPath();
    } else {
      BlazeUserSettings settings = BlazeUserSettings.getInstance();
      binaryPath = settings.getBazelBinaryPath();
    }
    return new BazelInvoker(binaryPath);
  }

  @Override
  public Optional<BuildInvoker> getParallelBuildInvoker(Project project, BlazeInfo blazeInfo) {
    return Optional.empty();
  }

  @Nullable
  static File getProjectSpecificBazelBinary(Project project) {
    ProjectViewSet projectView = ProjectViewManager.getInstance(project).getProjectViewSet();
    if (projectView == null) {
      return null;
    }
    return projectView.getScalarValue(BazelBinarySection.KEY).orElse(null);
  }
}
