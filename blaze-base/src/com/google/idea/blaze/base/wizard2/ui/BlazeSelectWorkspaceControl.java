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
package com.google.idea.blaze.base.wizard2.ui;

import com.google.common.collect.Lists;
import com.google.idea.blaze.base.ui.BlazeValidationResult;
import com.google.idea.blaze.base.wizard2.BlazeNewProjectBuilder;
import com.google.idea.blaze.base.wizard2.BlazeSelectWorkspaceOption;
import com.google.idea.blaze.base.wizard2.BlazeWizardOptionProvider;

import javax.swing.*;
import java.util.Collection;


/**
 * UI for selecting a client during the import process.
 */
public class BlazeSelectWorkspaceControl {
  BlazeSelectOptionControl<BlazeSelectWorkspaceOption> selectOptionControl;

  public BlazeSelectWorkspaceControl(BlazeNewProjectBuilder builder) {
    Collection<BlazeSelectWorkspaceOption> options = Lists.newArrayList();
    for (BlazeWizardOptionProvider optionProvider : BlazeWizardOptionProvider.EP_NAME.getExtensions()) {
      options.addAll(optionProvider.getSelectWorkspaceOptions(builder));
    }

    this.selectOptionControl =
      new BlazeSelectOptionControl<BlazeSelectWorkspaceOption>(builder, options) {
        @Override
        String getTitle() {
          return "Select workspace";
        }

        @Override
        String getOptionKey() {
          return "select-workspace.selected-option";
        }
      };
  }

  public JComponent getUiComponent() {
    return selectOptionControl.getUiComponent();
  }

  public BlazeValidationResult validate() {
    return selectOptionControl.validate();
  }

  public void updateBuilder(BlazeNewProjectBuilder builder) {
    builder.setWorkspaceOption(selectOptionControl.getSelectedOption());
  }

  public void commit() {
    selectOptionControl.commit();
  }
}
