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
package com.google.idea.blaze.base.plugin;

import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.common.actions.ReplaceActionHelper;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.impl.ActionConfigurationCustomizer;
import org.jetbrains.annotations.NotNull;

/**
 * {@link BlazeActionCustomizer} customizes some default IntelliJ actions to work well in the
 * context of Blaze projects.
 */
public class BlazeActionCustomizer implements ActionConfigurationCustomizer {

  @Override
  public void customize(@NotNull ActionManager actionManager) {
    hideMakeActions(actionManager);
  }

  // The original actions will be visible only on plain IDEA projects.
  private static void hideMakeActions(@NotNull ActionManager actionManager) {
    // 'Build' > 'Make Project' action
    ReplaceActionHelper.conditionallyHideAction(
        actionManager, "CompileDirty", Blaze::isBlazeProject);

    // 'Build' > 'Make Modules' action
    ReplaceActionHelper.conditionallyHideAction(
        actionManager, IdeActions.ACTION_MAKE_MODULE, Blaze::isBlazeProject);

    // 'Build' > 'Rebuild' action
    ReplaceActionHelper.conditionallyHideAction(
        actionManager, IdeActions.ACTION_COMPILE_PROJECT, Blaze::isBlazeProject);

    // 'Build' > 'Compile Modules' action
    ReplaceActionHelper.conditionallyHideAction(
        actionManager, IdeActions.ACTION_COMPILE, Blaze::isBlazeProject);
  }
}
