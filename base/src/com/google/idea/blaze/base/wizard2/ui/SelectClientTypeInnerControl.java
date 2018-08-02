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
package com.google.idea.blaze.base.wizard2.ui;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.wizard2.BlazeNewProjectBuilder;
import com.google.idea.blaze.base.wizard2.BlazeSelectWorkspaceOption;
import com.google.idea.blaze.base.wizard2.BlazeWizardUserSettings;

/**
 * There are now generally two layers of workspace types (e.g. [client type], then new/existing
 * [client type]). This handles UI and serialization for the inner layer.
 */
public abstract class SelectClientTypeInnerControl
    extends BlazeSelectOptionControl<BlazeSelectWorkspaceOption> {

  private final ImmutableList<BlazeSelectWorkspaceOption> options;

  /** @param options a list of mutually exclusive options associated with the parent option. */
  protected SelectClientTypeInnerControl(
      BlazeNewProjectBuilder builder, ImmutableList<BlazeSelectWorkspaceOption> options) {
    super(builder, options);
    this.options = options;
  }

  public boolean migratePreviousOptions(BlazeWizardUserSettings userSettings) {
    boolean select = false;
    for (BlazeSelectWorkspaceOption option : options) {
      boolean selected = option.migratePreviousOptions(userSettings);
      if (selected) {
        setSelectedOption(option);
      }
      select |= selected;
    }
    return select;
  }

  private void setSelectedOption(BlazeSelectWorkspaceOption option) {
    for (OptionUiEntry<BlazeSelectWorkspaceOption> entry : optionUiEntryList) {
      if (entry.option.equals(option)) {
        entry.radioButton.setSelected(true);
        return;
      }
    }
  }

  public void optionSelected() {
    for (OptionUiEntry<BlazeSelectWorkspaceOption> entry : optionUiEntryList) {
      if (entry.radioButton.isSelected()) {
        entry.option.optionSelected();
      } else {
        entry.option.optionDeselected();
      }
    }
  }
}
