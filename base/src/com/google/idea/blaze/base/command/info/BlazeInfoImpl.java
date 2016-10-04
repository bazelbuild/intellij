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
import com.google.idea.blaze.base.async.executor.BlazeExecutor;
import com.google.idea.blaze.base.async.process.ExternalTask;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.Blaze.BuildSystem;
import com.intellij.openapi.diagnostic.Logger;
import java.io.ByteArrayOutputStream;
import java.util.List;
import javax.annotation.Nullable;

class BlazeInfoImpl extends BlazeInfo {
  private static final Logger LOG = Logger.getInstance(BlazeInfoImpl.class);

  @Override
  public ListenableFuture<String> runBlazeInfo(
      @Nullable BlazeContext context,
      BuildSystem buildSystem,
      WorkspaceRoot workspaceRoot,
      List<String> blazeFlags,
      String key) {
    return BlazeExecutor.getInstance()
        .submit(
            () ->
                runBlazeInfo(buildSystem, workspaceRoot, key, blazeFlags, context)
                    .toString()
                    .trim());
  }

  @Override
  public ListenableFuture<byte[]> runBlazeInfoGetBytes(
      @Nullable BlazeContext context,
      BuildSystem buildSystem,
      WorkspaceRoot workspaceRoot,
      List<String> blazeFlags,
      String key) {
    return BlazeExecutor.getInstance()
        .submit(
            () -> runBlazeInfo(buildSystem, workspaceRoot, key, blazeFlags, context).toByteArray());
  }

  @Override
  public ListenableFuture<ImmutableMap<String, String>> runBlazeInfo(
      @Nullable BlazeContext context,
      BuildSystem buildSystem,
      WorkspaceRoot workspaceRoot,
      List<String> blazeFlags) {
    return BlazeExecutor.getInstance()
        .submit(
            () -> {
              String blazeInfoString =
                  runBlazeInfo(buildSystem, workspaceRoot, null /* key */, blazeFlags, context)
                      .toString()
                      .trim();
              return parseBlazeInfoResult(blazeInfoString);
            });
  }

  private static ByteArrayOutputStream runBlazeInfo(
      BuildSystem buildSystem,
      WorkspaceRoot workspaceRoot,
      @Nullable String key,
      List<String> blazeFlags,
      @Nullable BlazeContext context)
      throws BlazeInfoException {
    BlazeCommand.Builder builder = BlazeCommand.builder(buildSystem, BlazeCommandName.INFO);
    if (key != null) {
      builder.addBlazeFlags(key);
    }
    BlazeCommand command = builder.addBlazeFlags(blazeFlags).build();
    ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    ByteArrayOutputStream stderr = new ByteArrayOutputStream();
    int exitCode =
        ExternalTask.builder(workspaceRoot)
            .addBlazeCommand(command)
            .context(context)
            .stdout(stdout)
            .stderr(stderr)
            .build()
            .run();
    if (exitCode != 0) {
      throw new BlazeInfoException(exitCode, stdout.toString(), stderr.toString());
    }
    return stdout;
  }

  private static ImmutableMap<String, String> parseBlazeInfoResult(String blazeInfoString) {
    ImmutableMap.Builder<String, String> blazeInfoMapBuilder = ImmutableMap.builder();
    String[] blazeInfoLines = blazeInfoString.split("\n");
    for (String blazeInfoLine : blazeInfoLines) {
      // Just split on the first ":".
      String[] keyValue = blazeInfoLine.split(":", 2);
      LOG.assertTrue(keyValue.length == 2, blazeInfoLine);
      String key = keyValue[0].trim();
      String value = keyValue[1].trim();
      blazeInfoMapBuilder.put(key, value);
    }
    return blazeInfoMapBuilder.build();
  }
}
