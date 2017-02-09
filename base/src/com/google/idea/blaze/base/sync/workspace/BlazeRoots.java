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
package com.google.idea.blaze.base.sync.workspace;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.google.idea.blaze.base.io.FileAttributeProvider;
import com.google.idea.blaze.base.model.primitives.ExecutionRootPath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.settings.Blaze.BuildSystem;
import com.intellij.openapi.diagnostic.Logger;
import java.io.File;
import java.io.Serializable;
import java.util.List;

/** The data output by BlazeInfo. */
public class BlazeRoots implements Serializable {
  public static final long serialVersionUID = 3L;
  private static final Logger logger = Logger.getInstance(BlazeRoots.class);

  public static BlazeRoots build(
      BuildSystem buildSystem,
      WorkspaceRoot workspaceRoot,
      ImmutableMap<String, String> blazeInfo) {
    return build(
        workspaceRoot,
        getOrThrow(buildSystem, blazeInfo, BlazeInfo.EXECUTION_ROOT_KEY),
        getOrThrow(buildSystem, blazeInfo, BlazeInfo.PACKAGE_PATH_KEY),
        getOrThrow(buildSystem, blazeInfo, BlazeInfo.blazeBinKey(buildSystem)),
        getOrThrow(buildSystem, blazeInfo, BlazeInfo.blazeGenfilesKey(buildSystem)),
        getOrThrow(buildSystem, blazeInfo, BlazeInfo.OUTPUT_BASE_KEY));
  }

  private static BlazeRoots build(
      WorkspaceRoot workspaceRoot,
      String execRootString,
      String packagePathString,
      String blazeBinRoot,
      String blazeGenfilesRoot,
      String externalSourceRoot) {
    List<File> packagePaths = parsePackagePaths(workspaceRoot.toString(), packagePathString.trim());
    File executionRoot = new File(execRootString.trim());
    ExecutionRootPath blazeBinExecutionRootPath =
        ExecutionRootPath.createAncestorRelativePath(executionRoot, new File(blazeBinRoot));
    ExecutionRootPath blazeGenfilesExecutionRootPath =
        ExecutionRootPath.createAncestorRelativePath(executionRoot, new File(blazeGenfilesRoot));
    File externalSourceRootFile = new File(externalSourceRoot.trim());
    logger.assertTrue(blazeBinExecutionRootPath != null);
    logger.assertTrue(blazeGenfilesExecutionRootPath != null);
    return new BlazeRoots(
        executionRoot,
        packagePaths,
        blazeBinExecutionRootPath,
        blazeGenfilesExecutionRootPath,
        externalSourceRootFile);
  }

  private static String getOrThrow(
      BuildSystem buildSystem, ImmutableMap<String, String> map, String key) {
    String value = map.get(key);
    if (value == null) {
      throw new RuntimeException(
          String.format("Could not locate %s in %s info", key, buildSystem.getLowerCaseName()));
    }
    return value;
  }

  private static List<File> parsePackagePaths(String workspaceRoot, String packagePathString) {
    String[] paths = packagePathString.split(":");
    List<File> packagePaths = Lists.newArrayListWithCapacity(paths.length);
    FileAttributeProvider fileAttributeProvider = FileAttributeProvider.getInstance();
    for (String path : paths) {
      File packagePath = new File(path.replace("%workspace%", workspaceRoot));
      if (fileAttributeProvider.exists(packagePath)) {
        packagePaths.add(packagePath);
      }
    }
    return packagePaths;
  }

  public final File executionRoot;
  public final List<File> packagePaths;
  public final ExecutionRootPath blazeBinExecutionRootPath;
  public final ExecutionRootPath blazeGenfilesExecutionRootPath;
  public final File externalSourceRoot;

  @VisibleForTesting
  public BlazeRoots(
      File executionRoot,
      List<File> packagePaths,
      ExecutionRootPath blazeBinExecutionRootPath,
      ExecutionRootPath blazeGenfilesExecutionRootPath,
      File externalSourceRoot) {
    this.executionRoot = executionRoot;
    this.packagePaths = packagePaths;
    this.blazeBinExecutionRootPath = blazeBinExecutionRootPath;
    this.blazeGenfilesExecutionRootPath = blazeGenfilesExecutionRootPath;
    this.externalSourceRoot = externalSourceRoot;
  }

  public File getGenfilesDirectory() {
    return blazeGenfilesExecutionRootPath.getFileRootedAt(executionRoot);
  }

  public File getBlazeBinDirectory() {
    return blazeBinExecutionRootPath.getFileRootedAt(executionRoot);
  }

  public boolean isOutputArtifact(ExecutionRootPath path) {
    return ExecutionRootPath.isAncestor(blazeGenfilesExecutionRootPath, path, false)
        || ExecutionRootPath.isAncestor(blazeBinExecutionRootPath, path, false);
  }
}
