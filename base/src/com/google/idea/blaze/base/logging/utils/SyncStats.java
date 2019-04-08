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
package com.google.idea.blaze.base.logging.utils;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspaceType;
import com.google.idea.blaze.base.scope.scopes.TimingScopeListener.TimedEvent;
import com.google.idea.blaze.base.settings.BuildBinaryType;
import com.google.idea.blaze.base.sync.SyncMode;
import com.google.idea.blaze.base.sync.SyncResult;
import java.util.ArrayList;
import java.util.List;

/** A class to bundle the sync timing statistics for logging. */
@AutoValue
public abstract class SyncStats {

  public abstract ImmutableList<TargetExpression> workingSetTargets();

  public abstract ImmutableList<LanguageClass> languagesActive();

  public abstract ImmutableList<TargetExpression> blazeProjectTargets();

  public abstract BuildBinaryType syncBinaryType();

  public abstract ImmutableList<String> syncFlags();

  public abstract ImmutableList<TimedEvent> timedEvents();

  public abstract long totalExecTimeMs();

  public abstract long blazeExecTimeMs();

  public abstract long startTimeInEpochTime();

  public abstract SyncMode syncMode();

  public abstract String syncTitle();

  public abstract SyncResult syncResult();

  public abstract boolean syncSharded();

  public abstract WorkspaceType workspaceType();

  public static Builder builder() {
    return new AutoValue_SyncStats.Builder()
        .setTotalExecTimeMs(0L)
        .setBlazeExecTimeMs(0L)
        .setStartTimeInEpochTime(System.currentTimeMillis())
        .setWorkspaceType(WorkspaceType.JAVA)
        .setSyncMode(SyncMode.STARTUP)
        .setSyncBinaryType(BuildBinaryType.BLAZE_CUSTOM)
        .setSyncSharded(false)
        .setSyncFlags(new ArrayList<>())
        .setLanguagesActive(new ArrayList<>())
        .setBlazeProjectTargets(new ArrayList<>())
        .setWorkingSetTargets(new ArrayList<>());
  }
  /** Auto value builder for SyncStats. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setWorkingSetTargets(List<TargetExpression> workingSetTargets);

    public abstract Builder setLanguagesActive(List<LanguageClass> languagesActive);

    public abstract Builder setBlazeProjectTargets(List<TargetExpression> blazeProjectTargets);

    public abstract Builder setSyncBinaryType(BuildBinaryType binaryType);

    public abstract Builder setSyncFlags(List<String> syncFlags);

    public abstract Builder setTotalExecTimeMs(long totalExecTimeMs);

    public abstract Builder setBlazeExecTimeMs(long blazeExecTimeMs);

    public abstract Builder setStartTimeInEpochTime(long startTimeInEpochTime);

    abstract ImmutableList.Builder<TimedEvent> timedEventsBuilder();

    public Builder addTimedEvents(List<TimedEvent> timedEvents) {
      timedEventsBuilder().addAll(timedEvents);
      return this;
    }

    public abstract Builder setSyncMode(SyncMode syncMode);

    public abstract Builder setSyncTitle(String syncTitle);

    public abstract Builder setSyncResult(SyncResult syncResult);

    public abstract Builder setSyncSharded(boolean syncSharded);

    public abstract Builder setWorkspaceType(WorkspaceType workspaceType);

    public abstract SyncStats build();
  }
}
