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
package com.google.idea.sdkcompat.vcs;

import com.intellij.dvcs.actions.DvcsQuickListContentProvider;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import java.util.List;

/** #api183: method renamed / signature changed in 2019.1 */
public abstract class DvcsQuickListContentProviderAdapter extends DvcsQuickListContentProvider {

  @Override
  protected void addVcsSpecificActions(ActionManager actionManager, List<AnAction> actions) {
    addVcsSpecificActionsImpl(actionManager, actions);
  }

  protected abstract void addVcsSpecificActionsImpl(
      ActionManager actionManager, List<AnAction> actions);
}
