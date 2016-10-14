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
package com.google.idea.blaze.java.syncstatus;

import com.intellij.openapi.fileEditor.impl.EditorTabColorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import java.awt.Color;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Changes the color for unsynced files. */
public class BlazeJavaSyncStatusEditorTabColorProvider implements EditorTabColorProvider {
  private static final JBColor UNSYNCED_COLOR =
      new JBColor(new Color(252, 234, 234), new Color(121, 105, 105));

  @Nullable
  @Override
  public Color getEditorTabColor(@NotNull Project project, @NotNull VirtualFile file) {
    if (file.getName().endsWith(".java")
        && SyncStatusHelper.getInstance(project).isUnsynced(file)) {
      return UNSYNCED_COLOR;
    }
    return null;
  }
}
