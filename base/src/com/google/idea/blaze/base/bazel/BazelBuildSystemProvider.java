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
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.google.idea.blaze.base.lang.buildfile.language.semantics.RuleDefinition;
import com.google.idea.blaze.base.model.BlazeVersionData;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.settings.BlazeUserSettings;
import com.google.idea.blaze.base.settings.BuildSystem;

/** Provides the bazel build system name string. */
public class BazelBuildSystemProvider implements BuildSystemProvider {

  private static final String BAZEL_DOC_SITE = "https://ij.bazel.build/docs";

  private static final ImmutableSet<String> BUILD_FILE_NAMES =
      ImmutableSet.of("BUILD", "BUILD.bazel");

  @Override
  public BuildSystem buildSystem() {
    return BuildSystem.Bazel;
  }

  @Override
  public String getBinaryPath() {
    BlazeUserSettings settings = BlazeUserSettings.getInstance();
    return settings.getBazelBinaryPath();
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
  public ImmutableSet<String> possibleBuildFileNames() {
    return BUILD_FILE_NAMES;
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
