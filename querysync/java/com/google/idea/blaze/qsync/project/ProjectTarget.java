/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.qsync.project;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.common.BuildTarget;
import com.google.idea.blaze.common.Label;

/**
 * A build target that's included in the project.
 *
 * <p>This class augments {@link BuildTarget} which is shared with legacy sync, adding extra data
 * required by query sync.
 */
@AutoValue
public abstract class ProjectTarget {

  public abstract BuildTarget buildTarget();

  /** All the dependencies of a rule. */
  public abstract ImmutableSet<Label> deps();

  /** All the runtime dependencies of a java rule. */
  public abstract ImmutableSet<Label> runtimeDeps();

  public abstract ImmutableSet<Label> sourceLabels();

  public static Builder builder() {
    return new AutoValue_ProjectTarget.Builder();
  }

  /** Builder for {@link ProjectTarget}. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract BuildTarget.Builder buildTargetBuilder();

    public abstract ImmutableSet.Builder<Label> depsBuilder();

    public abstract ImmutableSet.Builder<Label> runtimeDepsBuilder();

    public abstract ImmutableSet.Builder<Label> sourceLabelsBuilder();

    public abstract ProjectTarget build();
  }
}
