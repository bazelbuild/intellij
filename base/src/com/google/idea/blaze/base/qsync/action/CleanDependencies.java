/*
 * Copyright 2022 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.qsync.action;

import com.google.idea.blaze.base.qsync.ArtifactTracker;
import com.google.idea.blaze.base.qsync.QuerySyncManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAwareAction;
import java.io.IOException;
import org.jetbrains.annotations.NotNull;

/** An internal action to clean query sync dependencies */
public final class CleanDependencies extends DumbAwareAction {

  static final Logger logger = Logger.getInstance(CleanDependencies.class.getName());

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    ArtifactTracker artifactTracker =
        QuerySyncManager.getInstance(e.getProject()).getArtifactTracker();
    try {
      artifactTracker.clear();
    } catch (IOException ex) {
      logger.warn("Failed to invalidate dependencies", ex);
    }
  }
}
