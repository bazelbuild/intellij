/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.gazelle.sync;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.sync.BlazeSyncIntegrationTestCase;
import com.google.idea.blaze.base.sync.BlazeSyncParams;
import com.google.idea.blaze.base.sync.SyncMode;
import com.google.idea.blaze.gazelle.GazelleUserSettings;
import java.io.File;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class RunGazelleOnSyncTest extends BlazeSyncIntegrationTestCase {

  @Override
  protected boolean isLightTestCase() {
    return false;
  }

  @Before
  public void setUpRepoWithGazelle() {
    workspace.createFile(
        new WorkspacePath("BUILD.bazel"),
        "load(\"@bazel_gazelle//:def.bzl\", \"gazelle\")",
        "# gazelle:prefix github.com/bazelbuild/intellij",
        "gazelle(name = \"gazelle\")");
    workspace.createFile(
        new WorkspacePath("WORKSPACE"),
        "load(\"@bazel_tools//tools/build_defs/repo:http.bzl\", \"http_archive\")",
        "",
        "http_archive(",
        "    name = \"io_bazel_rules_go\",",
        "    sha256 = \"2b1641428dff9018f9e85c0384f03ec6c10660d935b750e3fa1492a281a53b0f\",",
        "    urls = [",
        "       "
            + " \"https://mirror.bazel.build/github.com/bazelbuild/rules_go/releases/download/v0.29.0/rules_go-v0.29.0.zip\",",
        "       "
            + " \"https://github.com/bazelbuild/rules_go/releases/download/v0.29.0/rules_go-v0.29.0.zip\",",
        "    ],",
        ")",
        "",
        "http_archive(",
        "    name = \"bazel_gazelle\",",
        "    sha256 = \"de69a09dc70417580aabf20a28619bb3ef60d038470c7cf8442fafcf627c21cb\",",
        "    urls = [",
        "       "
            + " \"https://mirror.bazel.build/github.com/bazelbuild/bazel-gazelle/releases/download/v0.24.0/bazel-gazelle-v0.24.0.tar.gz\",",
        "       "
            + " \"https://github.com/bazelbuild/bazel-gazelle/releases/download/v0.24.0/bazel-gazelle-v0.24.0.tar.gz\",",
        "    ],",
        ")",
        "",
        "load(\"@io_bazel_rules_go//go:deps.bzl\", \"go_register_toolchains\","
            + " \"go_rules_dependencies\")",
        "load(\"@bazel_gazelle//:deps.bzl\", \"gazelle_dependencies\", \"go_repository\")",
        "go_rules_dependencies()",
        "go_register_toolchains(version = \"1.17.2\")",
        "gazelle_dependencies()");
    workspace.createDirectory(new WorkspacePath("src"));
    workspace.createFile(
        new WorkspacePath("src/main.go"),
        "package main",
        "import \"fmt\"",
        "func main() {",
        "  fmt.Println(\"Hello World\")",
        "}");
  }

  private void runBazelSync() {
    BlazeSyncParams syncParams =
        BlazeSyncParams.builder()
            .setTitle("Full Sync")
            .setSyncMode(SyncMode.INCREMENTAL)
            .setSyncOrigin("test")
            .setAddProjectViewTargets(true)
            .build();
    runBlazeSync(syncParams);
  }

  // Common directories so that the tests can cache the download of Gazelle.
  private static final String REPOSITORY_CACHE_LOCATION =
      FileUtils.getTempDirectoryPath() + "/temp_repository_cache";
  private static final String DISK_CACHE_LOCATION =
      FileUtils.getTempDirectoryPath() + "/temp_disk_cache";

  @BeforeClass
  public static void createCacheDirs() {
    ImmutableList<String> cachedirs =
        ImmutableList.of(REPOSITORY_CACHE_LOCATION, DISK_CACHE_LOCATION);
    for (String cachedir : cachedirs) {
      File directory = new File(cachedir);
      if (!directory.exists()) {
        directory.mkdirs();
      }
    }
  }

  @After
  public void clearState() {
    // Some test modify this global state, which doesn't get reset by regular cleanup.
    GazelleUserSettings.getInstance().clearGazelleTarget();
  }

  private static final String GENERATED_BUILD_FILE_PATH = "src/BUILD.bazel";
  private static final String WANTED_BUILD_CONTENTS =
      "load(\"@io_bazel_rules_go//go:def.bzl\", \"go_binary\", \"go_library\")\n"
          + "\n"
          + "go_library(\n"
          + "    name = \"src_lib\",\n"
          + "    srcs = [\"main.go\"],\n"
          + "    importpath = \"github.com/bazelbuild/intellij/src\",\n"
          + "    visibility = [\"//visibility:private\"],\n"
          + ")\n"
          + "\n"
          + "go_binary(\n"
          + "    name = \"src\",\n"
          + "    embed = [\":src_lib\"],\n"
          + "    visibility = [\"//visibility:public\"],\n"
          + ")\n";

  @Test
  public void testGazelleDoesntRunWhenNotConfigured() throws Exception {
    setProjectView(
        "directories:",
        "  .",
        "build_flags:",
        "  --repository_cache=" + REPOSITORY_CACHE_LOCATION,
        "  --disk_cache=" + DISK_CACHE_LOCATION);

    runBazelSync();

    errorCollector.assertNoIssues();

    File resultBuildFile = workspaceRoot.fileForPath(new WorkspacePath(GENERATED_BUILD_FILE_PATH));
    assertThat(resultBuildFile.exists()).isFalse();
  }

  @Test
  public void testRunGazelleWhenConfiguredInProject() throws Exception {
    setProjectView(
        "directories:",
        "  .",
        "gazelle_target: //:gazelle",
        "build_flags:",
        "  --repository_cache=" + REPOSITORY_CACHE_LOCATION,
        "  --disk_cache=" + DISK_CACHE_LOCATION);

    runBazelSync();

    errorCollector.assertNoIssues();

    File resultBuildFile = workspaceRoot.fileForPath(new WorkspacePath(GENERATED_BUILD_FILE_PATH));
    assertThat(resultBuildFile.exists()).isTrue();
    assertThat(workspaceRoot.isInWorkspace(resultBuildFile)).isTrue();
    assertThat(FileUtils.readFileToString(resultBuildFile, "UTF-8"))
        .contains(WANTED_BUILD_CONTENTS);
  }

  @Test
  public void testRunGazelleWhenConfiguredGlobally() throws Exception {
    GazelleUserSettings gazelleSettings = GazelleUserSettings.getInstance();
    gazelleSettings.setGazelleTarget("//:gazelle");

    setProjectView(
        "directories:",
        "  .",
        "build_flags:",
        "  --repository_cache=" + REPOSITORY_CACHE_LOCATION,
        "  --disk_cache=" + DISK_CACHE_LOCATION);

    runBazelSync();

    errorCollector.assertNoIssues();

    File resultBuildFile = workspaceRoot.fileForPath(new WorkspacePath("src/BUILD.bazel"));
    assertThat(resultBuildFile.exists()).isTrue();
    assertThat(workspaceRoot.isInWorkspace(resultBuildFile)).isTrue();
    assertThat(FileUtils.readFileToString(resultBuildFile, "UTF-8"))
        .contains(WANTED_BUILD_CONTENTS);
  }
}
