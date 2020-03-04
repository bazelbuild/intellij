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
package com.google.idea.sdkcompat.vcs;

import com.intellij.dvcs.ui.LightActionGroup;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Separator;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** #api182: DvcsBranchPopup changed to use LightActionGroup in 2018.3 */
public class DvcsPopupActionGroup extends ActionGroup {

  private final LightActionGroup delegate;

  DvcsPopupActionGroup(LightActionGroup delegate) {
    this.delegate = delegate;
  }

  @NotNull
  @Override
  public AnAction[] getChildren(@Nullable AnActionEvent e) {
    return delegate.getChildren(e);
  }

  public final void addAction(@NotNull AnAction action) {
    add(action);
  }

  public final void add(@NotNull AnAction action) {
    delegate.add(action);
  }

  public final void addAll(@NotNull ActionGroup group) {
    addAll(group.getChildren(null));
  }

  public final void addAll(@NotNull AnAction... actions) {
    delegate.addAll(Arrays.asList(actions));
  }

  public final void addAll(@NotNull List<? extends AnAction> actions) {
    delegate.addAll(actions);
  }

  public final void addSeparator() {
    add(Separator.create());
  }

  public void addSeparator(@Nullable String separatorText) {
    add(Separator.create(separatorText));
  }
}
