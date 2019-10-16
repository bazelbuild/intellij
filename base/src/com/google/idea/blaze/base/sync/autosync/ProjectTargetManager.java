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
package com.google.idea.blaze.base.sync.autosync;

import com.google.idea.blaze.base.model.primitives.Label;
import java.io.File;
import javax.annotation.Nullable;

/** Tracks and manages project targets for the purposes of automatic syncing. */
public interface ProjectTargetManager {

  /** A per-target / per-source sync status. */
  enum SyncStatus {
    UNSYNCED,
    IN_PROGRESS,
    STALE,
    SYNCED,
  }

  /** Returns the {@link SyncStatus} of the given target. */
  SyncStatus getSyncStatus(Label target);

  /**
   * Returns the {@link SyncStatus} of a given source file, or null if it can't be synced (e.g.
   * doesn't have a parent BUILD package in the workspace).
   */
  @Nullable
  SyncStatus getSyncStatus(File sourceFile);
}
