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
import com.google.idea.blaze.base.model.primitives.WorkspaceType;
import com.google.idea.blaze.base.scope.scopes.TimingScopeListener.TimedEvent;
import com.google.idea.blaze.base.settings.BuildBinaryType;
import com.google.idea.blaze.base.sync.SyncMode;
import com.google.idea.blaze.base.sync.SyncResult;
import java.util.List;

/** Sync stats covering all phases of sync. */
@AutoValue
public abstract class SyncStats {
  public abstract SyncMode syncMode();

  public abstract String syncTitle();

  public abstract BuildBinaryType syncBinaryType();

  public abstract SyncResult syncResult();

  public abstract ImmutableList<TimedEvent> timedEvents();

  public abstract long startTimeInEpochTime();

  public abstract long totalClockTimeMillis();

  public abstract long blazeExecTimeMillis();

  public abstract WorkspaceType workspaceType();

  public abstract ImmutableList<LanguageClass> languagesActive();

  public abstract ImmutableList<BuildPhaseSyncStats> buildPhaseStats();

  public static Builder builder() {
    return new AutoValue_SyncStats.Builder()
        .setBlazeExecTimeMillis(0)
        .setWorkspaceType(WorkspaceType.JAVA)
        .setLanguagesActive(ImmutableList.of());
  }

  /** Auto value builder for SyncStats. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setSyncMode(SyncMode syncMode);

    public abstract Builder setSyncTitle(String syncTitle);

    public abstract Builder setSyncBinaryType(BuildBinaryType binaryType);

    public abstract Builder setSyncResult(SyncResult syncResult);

    abstract ImmutableList.Builder<TimedEvent> timedEventsBuilder();

    public Builder addTimedEvents(List<TimedEvent> timedEvents) {
      timedEventsBuilder().addAll(timedEvents);
      return this;
    }

    public ImmutableList<TimedEvent> getCurrentTimedEvents() {
      return timedEventsBuilder().build();
    }

    public abstract Builder setStartTimeInEpochTime(long startTimeInEpochTime);

    public abstract Builder setTotalClockTimeMillis(long totalExecTimeMs);

    public abstract Builder setBlazeExecTimeMillis(long blazeExecTimeMs);

    public abstract Builder setWorkspaceType(WorkspaceType workspaceType);

    public abstract Builder setLanguagesActive(Iterable<LanguageClass> languagesActive);

    abstract ImmutableList.Builder<BuildPhaseSyncStats> buildPhaseStatsBuilder();

    public Builder addBuildPhaseStats(BuildPhaseSyncStats buildPhaseStats) {
      buildPhaseStatsBuilder().add(buildPhaseStats);
      return this;
    }

    public Builder addAllBuildPhaseStats(Iterable<BuildPhaseSyncStats> buildPhaseStats) {
      buildPhaseStatsBuilder().addAll(buildPhaseStats);
      return this;
    }

    public abstract SyncStats build();
  }
}
