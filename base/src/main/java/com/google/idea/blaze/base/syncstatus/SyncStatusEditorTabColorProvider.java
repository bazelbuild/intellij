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
package com.google.idea.blaze.base.syncstatus;

import com.google.idea.blaze.base.sync.autosync.ProjectTargetManager.SyncStatus;
import com.intellij.openapi.fileEditor.impl.EditorTabColorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import java.awt.Color;
import javax.annotation.Nullable;

/** Changes the color for unsynced files. */
public class SyncStatusEditorTabColorProvider implements EditorTabColorProvider {
  private static final JBColor UNSYNCED_COLOR =
      new JBColor(new Color(252, 234, 234), new Color(94, 56, 56));

  @Nullable
  @Override
  public Color getEditorTabColor(Project project, VirtualFile file) {
    if (SyncStatusContributor.getSyncStatus(project, file) == SyncStatus.UNSYNCED) {
      return UNSYNCED_COLOR;
    }
    return null;
  }
}
