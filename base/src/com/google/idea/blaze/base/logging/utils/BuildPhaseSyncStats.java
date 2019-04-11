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
package com.google.idea.blaze.base.logging.utils;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.scope.scopes.TimingScopeListener.TimedEvent;
import com.google.idea.blaze.base.sync.aspects.BuildResult;
import java.util.ArrayList;
import java.util.List;

/** Sync stats from a single build phase. */
@AutoValue
public abstract class BuildPhaseSyncStats {

  public abstract ImmutableList<TargetExpression> targets();

  public abstract ImmutableList<String> syncFlags();

  public abstract boolean syncSharded();

  public abstract BuildResult buildResult();

  public abstract ImmutableList<TimedEvent> timedEvents();

  public abstract long totalTimeMillis();

  public static Builder builder() {
    return new AutoValue_BuildPhaseSyncStats.Builder()
        .setTargets(ImmutableList.of())
        .setSyncFlags(new ArrayList<>())
        .setSyncSharded(false)
        .setBuildResult(BuildResult.FATAL_ERROR)
        .setTimedEvents(ImmutableList.of())
        .setTotalTimeMillis(0);
  }
  /** Auto value builder for SyncStats. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setTargets(List<TargetExpression> blazeProjectTargets);

    public abstract Builder setSyncFlags(List<String> syncFlags);

    public abstract Builder setSyncSharded(boolean syncSharded);

    public abstract Builder setBuildResult(BuildResult buildResult);

    public abstract Builder setTimedEvents(ImmutableList<TimedEvent> timedEvents);

    public abstract Builder setTotalTimeMillis(long totalTimeMillis);

    public abstract BuildPhaseSyncStats build();
  }
}
