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
package com.google.idea.blaze.base.command.info;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.base.model.primitives.ExecutionRootPath;
import com.google.idea.blaze.base.settings.Blaze.BuildSystem;
import com.intellij.openapi.diagnostic.Logger;
import java.io.File;
import java.io.Serializable;

/** The data output by blaze info. */
public class BlazeInfo implements Serializable {
  public static final long serialVersionUID = 2L;
  public static final String EXECUTION_ROOT_KEY = "execution_root";
  public static final String PACKAGE_PATH_KEY = "package_path";
  public static final String BUILD_LANGUAGE = "build-language";
  public static final String OUTPUT_BASE_KEY = "output_base";
  public static final String OUTPUT_PATH_KEY = "output_path";
  public static final String MASTER_LOG = "master-log";
  public static final String COMMAND_LOG = "command_log";
  public static final String RELEASE = "release";

  private static final Logger logger = Logger.getInstance(BlazeInfo.class);

  public static String blazeBinKey(BuildSystem buildSystem) {
    switch (buildSystem) {
      case Blaze:
        return "blaze-bin";
      case Bazel:
        return "bazel-bin";
      default:
        throw new IllegalArgumentException("Unrecognized build system: " + buildSystem);
    }
  }

  public static String blazeGenfilesKey(BuildSystem buildSystem) {
    switch (buildSystem) {
      case Blaze:
        return "blaze-genfiles";
      case Bazel:
        return "bazel-genfiles";
      default:
        throw new IllegalArgumentException("Unrecognized build system: " + buildSystem);
    }
  }

  public static String blazeTestlogsKey(BuildSystem buildSystem) {
    switch (buildSystem) {
      case Blaze:
        return "blaze-testlogs";
      case Bazel:
        return "bazel-testlogs";
      default:
        throw new IllegalArgumentException("Unrecognized build system: " + buildSystem);
    }
  }

  private final ImmutableMap<String, String> blazeInfoMap;

  private final File executionRoot;
  private final ExecutionRootPath blazeBinExecutionRootPath;
  private final ExecutionRootPath blazeGenfilesExecutionRootPath;
  private final File outputBase;

  public BlazeInfo(BuildSystem buildSystem, ImmutableMap<String, String> blazeInfoMap) {
    this.blazeInfoMap = blazeInfoMap;
    this.executionRoot = new File(getOrThrow(blazeInfoMap, EXECUTION_ROOT_KEY).trim());
    this.blazeBinExecutionRootPath =
        ExecutionRootPath.createAncestorRelativePath(
            executionRoot, new File(getOrThrow(blazeInfoMap, blazeBinKey(buildSystem))));
    this.blazeGenfilesExecutionRootPath =
        ExecutionRootPath.createAncestorRelativePath(
            executionRoot, new File(getOrThrow(blazeInfoMap, blazeGenfilesKey(buildSystem))));
    this.outputBase = new File(getOrThrow(blazeInfoMap, OUTPUT_BASE_KEY).trim());
    logger.assertTrue(blazeBinExecutionRootPath != null);
    logger.assertTrue(blazeGenfilesExecutionRootPath != null);
  }

  private static String getOrThrow(ImmutableMap<String, String> map, String key) {
    String value = map.get(key);
    if (value == null) {
      throw new RuntimeException(String.format("Could not locate %s in info map", key));
    }
    return value;
  }

  public String get(String key) {
    return blazeInfoMap.get(key);
  }

  public File getExecutionRoot() {
    return executionRoot;
  }

  public ExecutionRootPath getBlazeBinExecutionRootPath() {
    return blazeBinExecutionRootPath;
  }

  public ExecutionRootPath getBlazeGenfilesExecutionRootPath() {
    return blazeGenfilesExecutionRootPath;
  }

  public File getGenfilesDirectory() {
    return blazeGenfilesExecutionRootPath.getFileRootedAt(getExecutionRoot());
  }

  public File getBlazeBinDirectory() {
    return blazeBinExecutionRootPath.getFileRootedAt(getExecutionRoot());
  }

  public File getOutputBase() {
    return outputBase;
  }

  /** Creates a mock blaze info with the minimum information required for syncing. */
  @VisibleForTesting
  public static BlazeInfo createMockBlazeInfo(
      String outputBase, String executionRoot, String blazeBin, String blazeGenFiles) {
    BuildSystem buildSystem = BuildSystem.Bazel;
    ImmutableMap.Builder<String, String> blazeInfoMap =
        ImmutableMap.<String, String>builder()
            .put(OUTPUT_BASE_KEY, outputBase)
            .put(EXECUTION_ROOT_KEY, executionRoot)
            .put(blazeBinKey(buildSystem), blazeBin)
            .put(blazeGenfilesKey(buildSystem), blazeGenFiles);
    return new BlazeInfo(buildSystem, blazeInfoMap.build());
  }
}
