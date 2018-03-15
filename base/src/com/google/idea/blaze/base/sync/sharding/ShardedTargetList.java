/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.sync.sharding;

import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.scope.output.StatusOutput;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.aspects.BuildResult;
import com.intellij.openapi.project.Project;
import java.util.List;
import java.util.function.Function;

/** Partitioned list of blaze targets. */
public class ShardedTargetList {

  public final List<List<TargetExpression>> shardedTargets;

  public ShardedTargetList(List<List<TargetExpression>> shardedTargets) {
    this.shardedTargets = shardedTargets;
  }

  public boolean isEmpty() {
    return shardedTargets.stream().flatMap(List::stream).findFirst().orElse(null) == null;
  }

  /**
   * Runs the provided blaze invocation on each target list shard, returning the combined {@link
   * BuildResult}. Attempts to work around out of memory errors caused by lack of blaze garbage
   * collection where possible.
   */
  public BuildResult runShardedCommand(
      Project project,
      BlazeContext context,
      Function<Integer, String> progressMessage,
      Function<List<TargetExpression>, BuildResult> invocation) {
    if (isEmpty()) {
      return BuildResult.SUCCESS;
    }
    if (shardedTargets.size() == 1) {
      return invocation.apply(shardedTargets.get(0));
    }
    int progress = 0;
    BuildResult output = null;
    for (int i = 0; i < shardedTargets.size(); i++, progress++) {
      context.output(new StatusOutput(progressMessage.apply(i + 1)));
      BuildResult result = invocation.apply(shardedTargets.get(i));
      if (result.outOfMemory() && progress > 0) {
        // re-try now that blaze server has restarted
        progress = 0;
        IssueOutput.warn(retryOnOomMessage(project, i)).submit(context);
        result = invocation.apply(shardedTargets.get(i));
      }
      output = output == null ? result : BuildResult.combine(output, result);
      if (output.status == BuildResult.Status.FATAL_ERROR) {
        return output;
      }
    }
    return output;
  }

  private String retryOnOomMessage(Project project, int shardIndex) {
    String buildSystem = Blaze.buildSystemName(project);
    return String.format(
        "%s server ran out of memory on shard %s of %s. This is generally caused by %s garbage "
            + "collection bugs. Attempting to workaround by resuming with a clean %s server.",
        buildSystem, shardIndex + 1, shardedTargets.size(), buildSystem, buildSystem);
  }
}
