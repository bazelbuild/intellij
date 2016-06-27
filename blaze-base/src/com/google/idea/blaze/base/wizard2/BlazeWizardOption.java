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
package com.google.idea.blaze.base.wizard2;

import com.google.idea.blaze.base.ui.BlazeValidationResult;

import javax.annotation.Nullable;
import javax.swing.*;

/**
 * Base class for the workspace and project view options.
 */
public interface BlazeWizardOption {
  int HEADER_LABEL_WIDTH = 80;
  int MAX_INPUT_FIELD_WIDTH = 600;

  /**
   * @return A stable option name, used to remember which option was selected.
   */
  String getOptionName();

  /**
   * @return the option text, eg "Create workspace from scratch"
   */
  String getOptionText();

  /**
   * @return a ui component to be added below the corresponding radio button
   */
  @Nullable
  JComponent getUiComponent();

  BlazeValidationResult validate();
}
