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
package com.google.idea.blaze.base.run.targetfinder;

import com.google.common.util.concurrent.Futures;
import com.google.idea.blaze.base.async.process.ExternalTask;
import com.google.idea.blaze.base.async.process.LineProcessingOutputStream;
import com.google.idea.blaze.base.bazel.BuildSystem;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.qsync.QuerySync;
import com.google.idea.blaze.base.query.BlazeQueryLabelKindParser;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.common.PrintOutput;
import com.intellij.openapi.project.Project;
import java.util.Objects;
import java.util.concurrent.Future;
import javax.annotation.Nullable;

/** Uses the project's {@link TargetMap} to locate targets matching a given label. */
class ProjectTargetFinder implements TargetFinder {

  private static Future<TargetInfo> runBlazeQuery(Project project, Label label) {
    BuildSystem buildSystem = Blaze.getBuildSystemProvider(project).getBuildSystem();

    BlazeContext context = BlazeContext.create();
    BlazeCommand command =
            BlazeCommand.builder(
                            buildSystem.getDefaultInvoker(project, context), BlazeCommandName.QUERY)
                    .addBlazeFlags("--output=label_kind")
                    .addBlazeFlags(label.toString())
                    .build();

    BlazeQueryLabelKindParser outputProcessor = new BlazeQueryLabelKindParser(t -> true);

    int retVal =
            ExternalTask.builder(WorkspaceRoot.fromProject(project))
                    .addBlazeCommand(command)
                    .context(context)
                    .stdout(LineProcessingOutputStream.of(outputProcessor))
                    .stderr(
                            LineProcessingOutputStream.of(
                                    line -> {
                                      context.output(PrintOutput.error(line));
                                      return true;
                                    }))
                    .build()
                    .run();

    if (retVal == 0 && outputProcessor.getTargets().size() == 1) {
      return Futures.immediateFuture(outputProcessor.getTargets().get(0));
    }
    return Futures.immediateFuture(null);
  }

  @Override
  public Future<TargetInfo> findTarget(Project project, Label label) {
    // TODO(b/262428615): Retrieve data from query sync
    if (QuerySync.isEnabled()) {
      return Futures.immediateFuture(null);
    }
    BlazeProjectData projectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    TargetInfo target = projectData != null ? findTarget(projectData.getTargetMap(), label) : null;

    if (target == null) {
      // Query the target explicitly if it's excluded from the initial sync process (e.g.
      // has a manual tag - see https://github.com/bazelbuild/intellij/issues/4546
      return runBlazeQuery(project, label);
    }

    return Futures.immediateFuture(target);
  }

  @Nullable
  private static TargetInfo findTarget(TargetMap map, Label label) {
    // look for a plain target first
    TargetIdeInfo target = map.get(TargetKey.forPlainTarget(label));
    if (target != null) {
      return target.toTargetInfo();
    }
    // otherwise just return any matching target
    return map.targets().stream()
        .filter(t -> Objects.equals(label, t.getKey().getLabel()))
        .findFirst()
        .map(TargetIdeInfo::toTargetInfo)
        .orElse(null);
  }
}
