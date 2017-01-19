/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.run;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.Blaze.BuildSystem;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import java.util.List;
import javax.annotation.Nullable;

/** Information about any distributed executor available to the build system. */
public interface DistributedExecutorSupport {

  ExtensionPointName<DistributedExecutorSupport> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.DistributedExecutorSupport");

  /**
   * Returns the name of an available distributed executor, if one exists for the given build
   * system.
   */
  @Nullable
  static DistributedExecutorSupport getAvailableExecutor(BuildSystem buildSystem) {
    for (DistributedExecutorSupport executor : EP_NAME.getExtensions()) {
      if (executor.isAvailable(buildSystem)) {
        return executor;
      }
    }
    return null;
  }

  /** Returns the blaze/bazel flags required to specify whether to run on a distributed executor. */
  static List<String> getBlazeFlags(Project project, @Nullable Boolean runDistributed) {
    if (runDistributed == null) {
      return ImmutableList.of();
    }
    DistributedExecutorSupport executorInfo = getAvailableExecutor(Blaze.getBuildSystem(project));
    if (executorInfo == null) {
      return ImmutableList.of();
    }
    return ImmutableList.of(executorInfo.getBlazeFlag(runDistributed));
  }

  String executorName();

  boolean isAvailable(BuildSystem buildSystem);

  /** Get blaze/bazel flag specifying whether to run on this distributed executor */
  String getBlazeFlag(boolean runDistributed);
}
