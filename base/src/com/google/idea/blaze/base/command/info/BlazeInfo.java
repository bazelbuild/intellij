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

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.intellij.model.ProjectData;
import com.google.idea.blaze.base.ideinfo.ProtoWrapper;
import com.google.idea.blaze.base.model.primitives.ExecutionRootPath;
import com.google.idea.blaze.base.settings.BuildSystemName;
import java.io.File;

/** The data output by blaze info. */
@AutoValue
public abstract class BlazeInfo implements ProtoWrapper<ProjectData.BlazeInfo> {
  public static final String EXECUTION_ROOT_KEY = "execution_root";
  public static final String PACKAGE_PATH_KEY = "package_path";
  public static final String BUILD_LANGUAGE = "build-language";
  public static final String OUTPUT_BASE_KEY = "output_base";
  public static final String OUTPUT_PATH_KEY = "output_path";
  public static final String MASTER_LOG = "master-log";
  public static final String RELEASE = "release";

  public static String blazeBinKey(BuildSystemName buildSystemName) {
    switch (buildSystemName) {
      case Blaze:
        return "blaze-bin";
      case Bazel:
        return "bazel-bin";
    }
    throw new IllegalArgumentException("Unrecognized build system: " + buildSystemName);
  }

  public static String blazeGenfilesKey(BuildSystemName buildSystemName) {
    switch (buildSystemName) {
      case Blaze:
        return "blaze-genfiles";
      case Bazel:
        return "bazel-genfiles";
    }
    throw new IllegalArgumentException("Unrecognized build system: " + buildSystemName);
  }

  public static String blazeTestlogsKey(BuildSystemName buildSystemName) {
    switch (buildSystemName) {
      case Blaze:
        return "blaze-testlogs";
      case Bazel:
        return "bazel-testlogs";
    }
    throw new IllegalArgumentException("Unrecognized build system: " + buildSystemName);
  }

  public static BlazeInfo create(
      BuildSystemName buildSystemName, ImmutableMap<String, String> blazeInfoMap) {

    File executionRoot = new File(getOrThrow(blazeInfoMap, EXECUTION_ROOT_KEY).trim());
    ExecutionRootPath blazeBin =
        ExecutionRootPath.createAncestorRelativePath(
            executionRoot, new File(getOrThrow(blazeInfoMap, blazeBinKey(buildSystemName))));
    ExecutionRootPath blazeGenfiles =
        ExecutionRootPath.createAncestorRelativePath(
            executionRoot, new File(getOrThrow(blazeInfoMap, blazeGenfilesKey(buildSystemName))));
    ExecutionRootPath blazeTestlogs =
        ExecutionRootPath.createAncestorRelativePath(
            executionRoot, new File(getOrThrow(blazeInfoMap, blazeTestlogsKey(buildSystemName))));
    File outputBase = new File(getOrThrow(blazeInfoMap, OUTPUT_BASE_KEY).trim());
    return new AutoValue_BlazeInfo(
        blazeInfoMap, executionRoot, blazeBin, blazeGenfiles, blazeTestlogs, outputBase);
  }

  public static BlazeInfo fromProto(BuildSystemName buildSystemName, ProjectData.BlazeInfo proto) {
    return create(buildSystemName, ImmutableMap.copyOf(proto.getBlazeInfoMap()));
  }

  @Override
  public ProjectData.BlazeInfo toProto() {
    return ProjectData.BlazeInfo.newBuilder().putAllBlazeInfo(getBlazeInfoMap()).build();
  }

  private static String getOrThrow(ImmutableMap<String, String> map, String key) {
    String value = map.get(key);
    if (value == null) {
      throw new RuntimeException(String.format("Could not locate %s in info map", key));
    }
    return value;
  }

  abstract ImmutableMap<String, String> getBlazeInfoMap();

  public String get(String key) {
    return getBlazeInfoMap().get(key);
  }

  public abstract File getExecutionRoot();

  public abstract ExecutionRootPath getBlazeBin();

  public File getBlazeBinDirectory() {
    return getBlazeBin().getFileRootedAt(getExecutionRoot());
  }

  public abstract ExecutionRootPath getBlazeGenfiles();

  public File getGenfilesDirectory() {
    return getBlazeGenfiles().getFileRootedAt(getExecutionRoot());
  }

  public abstract ExecutionRootPath getBlazeTestlogs();

  public File getBlazeTestlogsDirectory() {
    return getBlazeTestlogs().getFileRootedAt(getExecutionRoot());
  }

  public abstract File getOutputBase();

  /** Creates a mock blaze info with the minimum information required for syncing. */
  @VisibleForTesting
  public static BlazeInfo createMockBlazeInfo(
      String outputBase,
      String executionRoot,
      String blazeBin,
      String blazeGenFiles,
      String blazeTestlogs) {
    BuildSystemName buildSystemName = BuildSystemName.Bazel;
    ImmutableMap.Builder<String, String> blazeInfoMap =
        ImmutableMap.<String, String>builder()
            .put(OUTPUT_BASE_KEY, outputBase)
            .put(EXECUTION_ROOT_KEY, executionRoot)
            .put(blazeBinKey(buildSystemName), blazeBin)
            .put(blazeGenfilesKey(buildSystemName), blazeGenFiles)
            .put(blazeTestlogsKey(buildSystemName), blazeTestlogs);
    return BlazeInfo.create(buildSystemName, blazeInfoMap.build());
  }
}
