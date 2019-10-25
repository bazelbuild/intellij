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
package com.google.idea.blaze.terminal;

import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.intellij.openapi.project.Project;
import java.util.Map;
import javax.annotation.Nullable;
import org.jetbrains.plugins.terminal.LocalTerminalCustomizer;

/** Set the default terminal path to the workspace root, and fixes $PATH tweaks by other plugins. */
public class TerminalCustomizer extends LocalTerminalCustomizer {

  private static final String FORCED_PATH = "_INTELLIJ_FORCE_SET_PATH";

  @Override
  public String[] customizeCommandAndEnvironment(
      Project project, String[] command, Map<String, String> envs) {

    // Some plugins forcibly override the $PATH variable after the user's rc files have run,
    // preventing customization. That's needlessly heavy-handed, since most users just append
    // or prepend to it. Respect the user's choice, and only set $PATH *before* the rc files.
    if (envs.containsKey(FORCED_PATH)) {
      String path = envs.get(FORCED_PATH);
      if (!path.isEmpty()) {
        envs.put("PATH", path);
      }
      envs.remove(FORCED_PATH);
    }

    return super.customizeCommandAndEnvironment(project, command, envs);
  }

  @Override
  @Nullable
  protected String getDefaultFolder(Project project) {
    WorkspaceRoot root = WorkspaceRoot.fromProjectSafe(project);
    return root != null ? root.toString() : null;
  }
}
