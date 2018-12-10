/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.java.libraries;

import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.sync.SyncListener;
import com.google.idea.blaze.base.sync.SyncMode;
import com.google.idea.blaze.base.sync.SyncResult;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.SimpleModificationTracker;

final class BlazeSyncModificationTracker {

  private final SimpleModificationTracker modificationTracker = new SimpleModificationTracker();

  static ModificationTracker getInstance(Project project) {
    return ServiceManager.getService(project, BlazeSyncModificationTracker.class)
        .modificationTracker;
  }

  static class SyncTrackerUpdater implements SyncListener {

    @Override
    public void afterSync(
        Project project, BlazeContext context, SyncMode syncMode, SyncResult syncResult) {
      ((SimpleModificationTracker) getInstance(project)).incModificationCount();
    }
  }
}
