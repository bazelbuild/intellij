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
package com.google.idea.blaze.base.sync;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import java.util.Collection;
import javax.annotation.concurrent.Immutable;

/** Parameters that control the sync. */
@Immutable
public final class BlazeSyncParams {

  /** The kind of sync. */
  public enum SyncMode {
    /** Happens on startup, restores in-memory state */
    STARTUP,
    /** Partial / working set sync */
    PARTIAL,
    /** This is the standard incremental sync */
    INCREMENTAL,
    /** Full sync, can invalidate/redo work that an incremental sync does not */
    FULL,
    /** A partial sync, without any blaze build (i.e. updates directories / in-memory state only) */
    NO_BUILD;

    public static boolean involvesBlazeBuild(SyncMode mode) {
      switch (mode) {
        case STARTUP:
        case NO_BUILD:
          return false;
        case PARTIAL:
        case INCREMENTAL:
        case FULL:
          return true;
      }
      throw new AssertionError("SyncMode not handled: " + mode);
    }
  }

  /** Builder for sync params */
  public static final class Builder {
    private String title;
    private SyncMode syncMode;
    private boolean backgroundSync;
    private boolean addProjectViewTargets;
    private boolean addWorkingSet;
    private ImmutableList.Builder<TargetExpression> targetExpressions = ImmutableList.builder();

    public static Builder copy(BlazeSyncParams params) {
      return new Builder(params.title, params.syncMode)
          .setBackgroundSync(params.backgroundSync)
          .addProjectViewTargets(params.addProjectViewTargets)
          .addTargetExpressions(params.targetExpressions)
          .addWorkingSet(params.addWorkingSet);
    }

    public Builder(String title, SyncMode syncMode) {
      this.title = title;
      this.syncMode = syncMode;
    }

    public Builder setSyncMode(SyncMode syncMode) {
      this.syncMode = syncMode;
      return this;
    }

    public Builder setTitle(String title) {
      this.title = title;
      return this;
    }

    public Builder setBackgroundSync(boolean backgroundSync) {
      this.backgroundSync = backgroundSync;
      return this;
    }

    public Builder addProjectViewTargets(boolean addProjectViewTargets) {
      this.addProjectViewTargets = addProjectViewTargets;
      return this;
    }

    public Builder addTargetExpression(TargetExpression targetExpression) {
      this.targetExpressions.add(targetExpression);
      return this;
    }

    public Builder addTargetExpressions(Collection<? extends TargetExpression> targets) {
      this.targetExpressions.addAll(targets);
      return this;
    }

    public Builder addWorkingSet(boolean addWorkingSet) {
      this.addWorkingSet = addWorkingSet;
      return this;
    }

    public BlazeSyncParams build() {
      return new BlazeSyncParams(
          title,
          syncMode,
          backgroundSync,
          addProjectViewTargets,
          addWorkingSet,
          targetExpressions.build());
    }
  }

  public final String title;
  public final SyncMode syncMode;
  public final boolean backgroundSync;
  public final boolean addProjectViewTargets;
  public final boolean addWorkingSet;
  public final ImmutableList<TargetExpression> targetExpressions;

  private BlazeSyncParams(
      String title,
      SyncMode syncMode,
      boolean backgroundSync,
      boolean addProjectViewTargets,
      boolean addWorkingSet,
      ImmutableList<TargetExpression> targetExpressions) {
    this.title = title;
    this.syncMode = syncMode;
    this.backgroundSync = backgroundSync;
    this.addProjectViewTargets = addProjectViewTargets;
    this.addWorkingSet = addWorkingSet;
    this.targetExpressions = targetExpressions;
  }

  @Override
  public String toString() {
    return String.format("%s (%s)", title, syncMode.name().toLowerCase());
  }
}
