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
package com.google.idea.blaze.base.bazel;

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.MustBeClosed;
import com.google.idea.blaze.base.bazel.BazelBuildSystem.BazelBinary;
import com.google.idea.blaze.base.command.BlazeCommandRunner;
import com.google.idea.blaze.base.command.CommandLineBlazeCommandRunner;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelperBep;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelperProvider;
import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.google.idea.blaze.base.lang.buildfile.language.semantics.RuleDefinition;
import com.google.idea.blaze.base.model.BlazeVersionData;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.section.sections.BazelBinarySection;
import com.google.idea.blaze.base.settings.BlazeUserSettings;
import com.google.idea.blaze.base.settings.BuildBinaryType;
import com.google.idea.blaze.base.settings.BuildSystem;
import com.intellij.openapi.project.Project;
import java.io.File;
import javax.annotation.Nullable;

/** Provides the bazel build system name string. */
public class BazelBuildSystemProvider implements BuildSystemProvider {

  private static final String BAZEL_DOC_SITE = "https://ij.bazel.build/docs";

  private static final ImmutableList<String> BUILD_FILE_NAMES =
      ImmutableList.of("BUILD.bazel", "BUILD");

  /** LocalBlazeBin TODO */
  public static class LocalBazelBin implements BazelBinary {
    private final String path;
    private final BuildResultHelperProvider buildResultHelperProvider;
    private final BlazeCommandRunner runner = new CommandLineBlazeCommandRunner();

    public LocalBazelBin(String path) {
      this.path = path;
      this.buildResultHelperProvider = new BuildResultHelperBep.Provider();
    }

    @Override
    public BuildBinaryType getType() {
      return BuildBinaryType.BAZEL;
    }

    @Override
    public String getPath() {
      return path;
    }

    @Override
    public boolean supportsParallelism() {
      return false;
    }

    @Override
    public BazelBinary setBlazeInfo(BlazeInfo blazeInfo) {
      return this;
    }

    @Override
    @MustBeClosed
    public BuildResultHelper createBuildResultProvider() {
      return buildResultHelperProvider.doCreate();
    }

    @Override
    public BlazeCommandRunner getCommandRunner() {
      return runner;
    }
  }

  /** BazelBinaryBuildSystem TODO */
  public static class BazelBinaryBuildSystem implements BazelBuildSystem {
    @Override
    public BuildSystem type() {
      return BuildSystem.Bazel;
    }

    @Override
    public BazelBinary getBinary(Project project, boolean requestParallelismSupport) {
      String binaryPath;
      File projectSpecificBinary = getProjectSpecificBazelBinary(project);
      if (projectSpecificBinary != null) {
        binaryPath = projectSpecificBinary.getPath();
      } else {
        BlazeUserSettings settings = BlazeUserSettings.getInstance();
        binaryPath = settings.getBazelBinaryPath();
      }
      return new LocalBazelBin(binaryPath);
    }

    @Override
    public SyncStrategy getSyncStrategy() {
      return SyncStrategy.SERIAL;
    }
  }

  private final BazelBuildSystem buildSystem = new BazelBinaryBuildSystem();

  @Override
  public BazelBuildSystem getBuildSystem() {
    return buildSystem;
  }

  @Override
  public BuildSystem buildSystem() {
    return BuildSystem.Bazel;
  }

  @Override
  public String getBinaryPath(Project project) {
    return getBuildSystem().getBinary(project, false).getPath();
  }

  @Nullable
  private static File getProjectSpecificBazelBinary(Project project) {
    ProjectViewSet projectView = ProjectViewManager.getInstance(project).getProjectViewSet();
    if (projectView == null) {
      return null;
    }
    return projectView.getScalarValue(BazelBinarySection.KEY).orElse(null);
  }

  @Override
  public BuildBinaryType getSyncBinaryType(boolean forceParallel) {
    return BuildBinaryType.BAZEL;
  }

  @Override
  public WorkspaceRootProvider getWorkspaceRootProvider() {
    return BazelWorkspaceRootProvider.INSTANCE;
  }

  @Override
  public ImmutableList<String> buildArtifactDirectories(WorkspaceRoot root) {
    String rootDir = root.directory().getName();
    return ImmutableList.of(
        "bazel-bin", "bazel-genfiles", "bazel-out", "bazel-testlogs", "bazel-" + rootDir);
  }

  @Override
  public String getRuleDocumentationUrl(RuleDefinition rule) {
    // TODO: URL pointing to specific BUILD rule.
    return "http://www.bazel.build/docs/be/overview.html";
  }

  @Override
  public String getProjectViewDocumentationUrl() {
    return BAZEL_DOC_SITE + "/project-views.html";
  }

  @Override
  public String getLanguageSupportDocumentationUrl(String relativeDocName) {
    return String.format("%s/%s.html", BAZEL_DOC_SITE, relativeDocName);
  }

  @Override
  public ImmutableList<String> possibleBuildFileNames() {
    return BUILD_FILE_NAMES;
  }

  @Override
  public ImmutableList<String> possibleWorkspaceFileNames() {
    return ImmutableList.of("WORKSPACE", "WORKSPACE.bazel");
  }

  @Override
  public void populateBlazeVersionData(
      BuildSystem buildSystem,
      WorkspaceRoot workspaceRoot,
      BlazeInfo blazeInfo,
      BlazeVersionData.Builder builder) {
    if (buildSystem != BuildSystem.Bazel) {
      return;
    }
    builder.setBazelVersion(BazelVersion.parseVersion(blazeInfo));
  }
}
