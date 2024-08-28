/*
 * Copyright 2020 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.command.mod;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.idea.blaze.base.async.executor.BlazeExecutor;
import com.google.idea.blaze.base.bazel.BuildSystem;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeCommandRunner;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper;
import com.google.idea.blaze.base.model.ExternalWorkspaceData;
import com.google.idea.blaze.base.model.primitives.ExternalWorkspace;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.intellij.openapi.project.Project;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class BlazeModRunnerImpl extends BlazeModRunner {

  @Override
  public ListenableFuture<ExternalWorkspaceData> dumpRepoMapping(
      Project project,
      BuildSystem.BuildInvoker invoker,
      BlazeContext context,
      BuildSystemName buildSystemName,
      List<String> flags) {
    return Futures.transform(
        runBlazeModGetBytes(project, invoker, context, ImmutableList.of( "dump_repo_mapping", "workspace"), flags),
        bytes -> {
          JsonObject json = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8).trim()).getAsJsonObject();

          ImmutableList<ExternalWorkspace> externalWorkspaces =
              json.entrySet().stream()
                  .filter(e -> e.getValue().isJsonPrimitive())
                  .filter(e -> !e.getValue().getAsString().trim().isEmpty())
                  .map(e -> ExternalWorkspace.create(e.getValue().getAsString(), e.getKey()))
                  .collect(ImmutableList.toImmutableList());

          return ExternalWorkspaceData.create(externalWorkspaces);
        },
        BlazeExecutor.getInstance().getExecutor());
  }

  @Override
  public ListenableFuture<byte[]> runBlazeModGetBytes(
      Project project,
      BuildSystem.BuildInvoker invoker,
      BlazeContext context,
      List<String> args,
      List<String> flags) {
    return BlazeExecutor.getInstance()
               .submit(() -> {
                 BlazeCommand.Builder builder =
                     BlazeCommand.builder(invoker, BlazeCommandName.MOD)
                         .addBlazeFlags(flags);

                 if (args != null) {
                   builder.addBlazeFlags(args);
                 }

                 try (BuildResultHelper buildResultHelper = invoker.createBuildResultHelper()) {
                   BlazeCommandRunner runner = invoker.getCommandRunner();
                   try (InputStream stream = runner.runBlazeMod(project, builder, buildResultHelper, context)) {
                     return stream.readAllBytes();
                   }
                 }
               });
  }
}
