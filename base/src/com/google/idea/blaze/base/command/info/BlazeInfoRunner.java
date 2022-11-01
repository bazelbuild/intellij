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

import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.intellij.openapi.components.ServiceManager;
import java.util.List;

/** Runs the blaze info command. The results may be cached in the workspace. */
public abstract class BlazeInfoRunner {

  public static BlazeInfoRunner getInstance() {
    return ServiceManager.getService(BlazeInfoRunner.class);
  }

  /**
   * @param blazeFlags The blaze flags that will be passed to Blaze.
   * @param key The key passed to blaze info
   * @return The blaze info value associated with the specified key
   */
  public abstract ListenableFuture<String> runBlazeInfo(
      BlazeContext context,
      String binaryPath,
      WorkspaceRoot workspaceRoot,
      List<String> blazeFlags,
      String key);

  /**
   * @param blazeFlags The blaze flags that will be passed to Blaze.
   * @param key The key passed to blaze info
   * @return The blaze info value associated with the specified key
   */
  public abstract ListenableFuture<byte[]> runBlazeInfoGetBytes(
      BlazeContext context,
      String binaryPath,
      WorkspaceRoot workspaceRoot,
      List<String> blazeFlags,
      String key);

  /**
   * This calls blaze info without any specific key so blaze info will return all keys and values
   * that it has.
   *
   * @param blazeFlags The blaze flags that will be passed to Blaze.
   * @return The blaze info data fields.
   */
  public abstract ListenableFuture<BlazeInfo> runBlazeInfo(
      BlazeContext context,
      BuildSystemName buildSystemName,
      String binaryPath,
      WorkspaceRoot workspaceRoot,
      List<String> blazeFlags);
}
