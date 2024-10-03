/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
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

  private static final String DUMP_REPO_MAPPING = "dump_repo_mapping";
  private static final String DEPS = "deps";
  private static final String ROOT_WORKSPACE = "";


  /**
   * For some reason, dump_repo_mapping reqiures "", while deps requires "<root>".
   * Probably a bazel bug
   */
  private static final String ROOT_WORKSPACE_EXPLICIT = "<root>";

  /**
   * {@code bazel mod dump_repo_mapping} takes a canonical repository name and will dump a map from
   * repoName -> canonicalName of all the external repositories available to that repository The
   * name {@code ""} is special and considered to be <em>the main workspace</em> so in order to dump
   * the main repository map we would invoke it like {@code bazel mod dump_repo_mapping ""}.
   *
   * <p>Additionally the flag {@code --enable_workspace} needs to be off for this to work. The flag
   * is default off in bazel 8.0.0 but it is on between 7.1.0 and 8.0.0. So we need to also pass
   * this along in between those versions for the command to work well.
   */
  @Override
  public ListenableFuture<ExternalWorkspaceData> dumpRepoMapping(
      Project project,
      BuildSystem.BuildInvoker invoker,
      BlazeContext context,
      BuildSystemName buildSystemName,
      List<String> flags) {

    // TODO: when 8.0.0 is released add this only if it's disabled explicitly for the repo
    flags.add("--noenable_workspace");

    return Futures.transform(
        runBlazeModGetBytes(
            project, invoker, context, ImmutableList.of(DUMP_REPO_MAPPING, ROOT_WORKSPACE), flags),
        bytes -> {
          JsonObject json =
              JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8).trim())
                  .getAsJsonObject();

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
    public ListenableFuture<String> getDeps(
            Project project,
            BuildSystem.BuildInvoker invoker,
            BlazeContext context,
            BuildSystemName buildSystemName,
            List<String> flags) {

        return Futures.transform(
                runBlazeModGetBytes(
                        project, invoker, context, ImmutableList.of(DEPS, ROOT_WORKSPACE_EXPLICIT, "--output=json"), flags),
                bytes -> new String(bytes, StandardCharsets.UTF_8),
                BlazeExecutor.getInstance().getExecutor()
        );

    }

  @Override
  protected ListenableFuture<byte[]> runBlazeModGetBytes(
      Project project,
      BuildSystem.BuildInvoker invoker,
      BlazeContext context,
      List<String> args,
      List<String> flags) {
    return BlazeExecutor.getInstance()
        .submit(
            () -> {
              BlazeCommand.Builder builder =
                  BlazeCommand.builder(invoker, BlazeCommandName.MOD, project).addBlazeFlags(flags);

              if (args != null) {
                builder.addBlazeFlags(args);
              }

              try (BuildResultHelper buildResultHelper = invoker.createBuildResultHelper()) {
                BlazeCommandRunner runner = invoker.getCommandRunner();
                try (InputStream stream =
                    runner.runBlazeMod(project, builder, buildResultHelper, context)) {
                  return stream.readAllBytes();
                }
              }
            });
  }
}
