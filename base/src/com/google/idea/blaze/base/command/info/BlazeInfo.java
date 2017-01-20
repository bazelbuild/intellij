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

import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.Blaze.BuildSystem;
import com.intellij.openapi.components.ServiceManager;
import java.util.List;
import javax.annotation.Nullable;

/** Runs the blaze info command. The results may be cached in the workspace. */
public abstract class BlazeInfo {
  public static final String EXECUTION_ROOT_KEY = "execution_root";
  public static final String PACKAGE_PATH_KEY = "package_path";
  public static final String BUILD_LANGUAGE = "build-language";
  public static final String OUTPUT_BASE_KEY = "output_base";
  public static final String MASTER_LOG = "master-log";
  public static final String RELEASE = "release";

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

  public static BlazeInfo getInstance() {
    return ServiceManager.getService(BlazeInfo.class);
  }

  /**
   * @param blazeFlags The blaze flags that will be passed to Blaze.
   * @param key The key passed to blaze info
   * @return The blaze info value associated with the specified key
   */
  public abstract ListenableFuture<String> runBlazeInfo(
      @Nullable BlazeContext context,
      BuildSystem buildSystem,
      WorkspaceRoot workspaceRoot,
      List<String> blazeFlags,
      String key);

  /**
   * @param blazeFlags The blaze flags that will be passed to Blaze.
   * @param key The key passed to blaze info
   * @return The blaze info value associated with the specified key
   */
  public abstract ListenableFuture<byte[]> runBlazeInfoGetBytes(
      @Nullable BlazeContext context,
      BuildSystem buildSystem,
      WorkspaceRoot workspaceRoot,
      List<String> blazeFlags,
      String key);

  /**
   * This calls blaze info without any specific key so blaze info will return all keys and values
   * that it has. There could be a performance cost for doing this, so the user should verify that
   * calling this method is actually faster than calling {@link #runBlazeInfo(WorkspaceRoot, List,
   * String)}.
   *
   * @param blazeFlags The blaze flags that will be passed to Blaze.
   * @return The blaze info data fields.
   */
  public abstract ListenableFuture<ImmutableMap<String, String>> runBlazeInfo(
      @Nullable BlazeContext context,
      BuildSystem buildSystem,
      WorkspaceRoot workspaceRoot,
      List<String> blazeFlags);
}
