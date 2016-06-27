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

import javax.annotation.concurrent.Immutable;
import java.util.Collection;

/**
 * Parameters that control the sync.
 */
@Immutable
public final class BlazeSyncParams {

  public enum SyncMode {
    RESTORE_EPHEMERAL_STATE,
    INCREMENTAL,
    FULL
  }

  public static final class Builder {
    private String title;
    private SyncMode syncMode;
    private boolean backgroundSync = false;
    private boolean doBuild = true;
    private ImmutableList.Builder<TargetExpression> targetExpressions = ImmutableList.builder();

    public Builder(String title,
                   SyncMode syncMode) {
      this.title = title;
      this.syncMode = syncMode;
    }

    public Builder setDoBuild(boolean doBuild) {
      this.doBuild = doBuild;
      return this;
    }

    public Builder setBackgroundSync(boolean backgroundSync) {
      this.backgroundSync = backgroundSync;
      return this;
    }

    public Builder addTargetExpression(TargetExpression targetExpression) {
      this.targetExpressions.add(targetExpression);
      return this;
    }

    public Builder addTargetExpressions(Collection<TargetExpression> targets) {
      this.targetExpressions.addAll(targets);
      return this;
    }

    public BlazeSyncParams build() {
      return new BlazeSyncParams(title, syncMode, backgroundSync, doBuild, targetExpressions.build());
    }
  }

  public final String title;
  public final SyncMode syncMode;
  public final boolean backgroundSync;
  public final boolean doBuild;
  public final ImmutableList<TargetExpression> targetExpressions;

  private BlazeSyncParams(
    String title,
    SyncMode syncMode,
    boolean backgroundSync,
    boolean doBuild,
    ImmutableList<TargetExpression> targetExpressions) {
    this.title = title;
    this.syncMode = syncMode;
    this.backgroundSync = backgroundSync;
    this.doBuild = doBuild;
    this.targetExpressions = targetExpressions;
  }
}
