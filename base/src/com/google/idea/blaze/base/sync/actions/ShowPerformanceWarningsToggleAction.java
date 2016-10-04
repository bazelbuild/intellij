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
package com.google.idea.blaze.base.sync.actions;

import com.google.idea.blaze.base.actions.BlazeToggleAction;
import com.google.idea.blaze.base.settings.BlazeUserSettings;
import com.intellij.openapi.actionSystem.AnActionEvent;

/** Manages a tick box of whether to show performance warnings. */
public class ShowPerformanceWarningsToggleAction extends BlazeToggleAction {
  @Override
  public boolean isSelected(AnActionEvent e) {
    return BlazeUserSettings.getInstance().getShowPerformanceWarnings();
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    BlazeUserSettings.getInstance().setShowPerformanceWarnings(state);
  }
}
