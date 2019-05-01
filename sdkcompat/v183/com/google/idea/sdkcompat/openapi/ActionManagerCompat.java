/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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
package com.google.idea.sdkcompat.openapi;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;

/** Compat for {@link ActionManager}. Remove when #api183 is no longer supported. */
public final class ActionManagerCompat {
  private ActionManagerCompat() {}

  // #api183: dedicated method introduced in 2019.1, and old approach broke
  public static void replaceAction(String actionId, AnAction newAction) {
    ActionManager actionManager = ActionManager.getInstance();
    actionManager.unregisterAction(actionId);
    actionManager.registerAction(actionId, newAction);
  }
}
