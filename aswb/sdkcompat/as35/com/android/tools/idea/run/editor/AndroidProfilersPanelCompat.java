/*
 * Copyright 2020 The Bazel Authors. All rights reserved.
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
package com.android.tools.idea.run.editor;

import com.intellij.openapi.project.Project;

/** Compat class for {@link com.android.tools.idea.run.editor.AndroidProfilersPanel} */
public class AndroidProfilersPanelCompat {
  private AndroidProfilersPanelCompat() {}

  public static AndroidProfilersPanel getNewAndroidProfilersPanel(
      Project project, ProfilerState state) {
    return new AndroidProfilersPanel(project, state);
  }

  public static void resetFrom(AndroidProfilersPanel profilersPanel, ProfilerState state) {
    profilersPanel.resetFrom(state);
  }

  public static void applyTo(AndroidProfilersPanel profilersPanel, ProfilerState state) {
    profilersPanel.applyTo(state);
  }
}
