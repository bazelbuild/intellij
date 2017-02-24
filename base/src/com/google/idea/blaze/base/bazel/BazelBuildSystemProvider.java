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
import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.base.io.FileAttributeProvider;
import com.google.idea.blaze.base.lang.buildfile.language.semantics.RuleDefinition;
import com.google.idea.blaze.base.model.BlazeVersionData;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.settings.Blaze.BuildSystem;
import com.google.idea.blaze.base.settings.BlazeUserSettings;
import com.intellij.openapi.fileTypes.ExactFileNameMatcher;
import com.intellij.openapi.fileTypes.FileNameMatcher;
import java.io.File;
import javax.annotation.Nullable;

/** Provides the bazel build system name string. */
public class BazelBuildSystemProvider implements BuildSystemProvider {

  @Override
  public BuildSystem buildSystem() {
    return BuildSystem.Bazel;
  }

  @Nullable
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

  @Nullable
  @Override
  public String getRuleDocumentationUrl(RuleDefinition rule) {
    // TODO: URL pointing to specific BUILD rule.
    return "http://www.bazel.build/docs/be/overview.html";
  }

  // TODO: Update the methods below when https://github.com/bazelbuild/bazel/issues/552 lands.
  @Override
  public boolean isBuildFile(String fileName) {
    return fileName.equals("BUILD");
  }

  @Nullable
  @Override
  public File findBuildFileInDirectory(File directory) {
    FileAttributeProvider provider = FileAttributeProvider.getInstance();
    File child = new File(directory, "BUILD");
    if (provider.exists(child)) {
      return child;
    }
    child = new File(directory, "BUILD.bazel");
    if (provider.exists(child)) {
      return child;
    }
    return null;
  }

  @Override
  public ImmutableList<FileNameMatcher> buildFileMatchers() {
    return ImmutableList.of(
        new ExactFileNameMatcher("BUILD"), new ExactFileNameMatcher("BUILD.bazel"));
  }

  @Override
  public void populateBlazeVersionData(
      BuildSystem buildSystem,
      WorkspaceRoot workspaceRoot,
      ImmutableMap<String, String> blazeInfo,
      BlazeVersionData.Builder builder) {
    if (buildSystem != BuildSystem.Bazel) {
      return;
    }
    builder.setBazelVersion(BazelVersion.parseVersion(blazeInfo));
  }
}
