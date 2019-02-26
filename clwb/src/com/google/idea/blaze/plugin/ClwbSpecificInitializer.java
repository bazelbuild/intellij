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
package com.google.idea.blaze.plugin;

import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.clwb.CMakeActionsToManipulate;
import com.google.idea.common.actions.ReplaceActionHelper;
import com.intellij.openapi.components.ApplicationComponent;

/** Runs on startup. */
public class ClwbSpecificInitializer implements ApplicationComponent {

  @Override
  public void initComponent() {
    hideCMakeActions();
  }

  // The original actions will be visible only on plain IDEA projects.
  private static void hideCMakeActions() {
    for (String actionId : CMakeActionsToManipulate.CMAKE_ACTION_IDS_TO_REMOVE) {
      ReplaceActionHelper.conditionallyHideAction(actionId, Blaze::isBlazeProject);
    }
    for (CMakeActionsToManipulate.ActionPair actionPair :
        CMakeActionsToManipulate.CMAKE_ACTION_IDS_TO_REPLACE) {
      ReplaceActionHelper.conditionallyReplaceAction(
          actionPair.id, actionPair.replacement.get(), Blaze::isBlazeProject);
    }
  }
}
