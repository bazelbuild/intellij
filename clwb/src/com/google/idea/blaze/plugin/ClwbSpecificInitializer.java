/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.plugin;

import com.google.idea.blaze.base.plugin.BlazeActionRemover;
import com.google.idea.sdkcompat.clion.CMakeActionList;
import com.intellij.openapi.components.ApplicationComponent;

/** Runs on startup. */
public class ClwbSpecificInitializer extends ApplicationComponent.Adapter {

  @Override
  public void initComponent() {
    hideCMakeActions();
  }

  // The original actions will be visible only on plain IDEA projects.
  private static void hideCMakeActions() {
    for (String actionId : CMakeActionList.CMAKE_ACTION_IDS) {
      BlazeActionRemover.hideAction(actionId);
    }
    BlazeActionRemover.replaceAction("OpenCPPProject", new CMakeOpenProjectActionOverride());
  }
}
