/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.dependencies;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.idea.blaze.base.async.process.ExternalTask;
import com.google.idea.blaze.base.async.process.LineProcessingOutputStream;
import com.google.idea.blaze.base.async.process.PrintOutputLineProcessor;
import com.google.idea.blaze.base.bazel.BuildSystemProvider;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.query.BlazeQueryLabelKindParser;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.Scope;
import com.google.idea.blaze.base.scope.scopes.IdeaLogScope;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.openapi.project.Project;
import java.util.List;
import java.util.concurrent.Future;
import javax.annotation.Nullable;
import org.jetbrains.ide.PooledThreadExecutor;

/**
 * Given a blaze target, runs a blaze query invocation to find the direct dependencies of that
 * target.
 *
 * <p>This is expected to be slow, so should be asynchronous and/or cancellable.
 */
class BlazeQueryDirectDependencyTargetProvider implements DirectDependencyTargetProvider {

  /**
   * Currently disabled for performance reasons. DirectDependencyTargetProvider is called often, in
   * the background, and we don't want to monopolize the local blaze server.
   */
  private static final BoolExperiment enabled =
      new BoolExperiment("use.blaze.query.for.directdeps", false);

  @Override
  public Future<List<TargetInfo>> getDirectDependencyTargets(Project project, Label target) {
    if (!enabled.getValue()) {
      return Futures.immediateFuture(null);
    }
    return PooledThreadExecutor.INSTANCE.submit(
        () ->
            Scope.root(
                context -> {
                  context.push(new IdeaLogScope());
                  return runQuery(project, target, context);
                }));
  }

  @Nullable
  private static ImmutableList<TargetInfo> runQuery(
      Project project, Label target, BlazeContext context) {
    String query =
        String.format("let x = \"%s\" in deps($x, 1) - $x - labels(\"exports\", $x)", target);
    BlazeCommand command =
        BlazeCommand.builder(getBinaryPath(project), BlazeCommandName.QUERY)
            .addBlazeFlags("--output=label_kind")
            .addBlazeFlags(query)
            .build();

    BlazeQueryLabelKindParser outputProcessor = new BlazeQueryLabelKindParser(t -> true);
    int retVal =
        ExternalTask.builder(WorkspaceRoot.fromProject(project))
            .addBlazeCommand(command)
            .context(context)
            .stdout(LineProcessingOutputStream.of(outputProcessor))
            .stderr(LineProcessingOutputStream.of(new PrintOutputLineProcessor(context)))
            .build()
            .run();
    if (retVal != 0) {
      return null;
    }
    return outputProcessor.getTargets();
  }

  private static String getBinaryPath(Project project) {
    BuildSystemProvider buildSystemProvider = Blaze.getBuildSystemProvider(project);
    return buildSystemProvider.getSyncBinaryPath(project);
  }
}
