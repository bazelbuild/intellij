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
package com.google.idea.blaze.base.ide;

import com.google.common.collect.Lists;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.TargetName;
import com.google.idea.blaze.base.ui.BlazeValidationError;
import com.google.idea.blaze.base.ui.UiUtil;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import java.util.Collection;
import java.util.List;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class NewRuleUI {

  private static final String[] POSSIBLE_RULES = {
    "android_library", "java_library", "cc_library", "cc_binary", "proto_library"
  };

  @NotNull private final ComboBox ruleComboBox = new ComboBox(POSSIBLE_RULES);
  @NotNull private final JBLabel ruleNameLabel = new JBLabel("Rule name:");
  @NotNull private final JBTextField ruleNameField;

  public NewRuleUI(int textFieldLength) {
    this.ruleNameField = new JBTextField(textFieldLength);
  }

  public void fillUI(@NotNull JPanel component, int indentLevel) {
    component.add(ruleNameLabel);
    component.add(ruleNameField, UiUtil.getFillLineConstraints(indentLevel));
    component.add(ruleComboBox, UiUtil.getFillLineConstraints(indentLevel));
  }

  @NotNull
  public Kind getSelectedRuleKind() {
    return Kind.fromString((String) ruleComboBox.getSelectedItem());
  }

  @NotNull
  public TargetName getRuleName() {
    return TargetName.create(ruleNameField.getText());
  }

  @Nullable
  public ValidationInfo validate() {
    String ruleName = ruleNameField.getText();
    List<BlazeValidationError> errors = Lists.newArrayList();
    if (!validateRuleName(ruleName, errors)) {
      BlazeValidationError issue = errors.get(0);
      return new ValidationInfo(issue.getError(), ruleNameField);
    }
    return null;
  }

  private static boolean validateRuleName(
      @NotNull String inputString, @Nullable Collection<BlazeValidationError> errors) {
    if (inputString.length() == 0) {
      BlazeValidationError.collect(
          errors, new BlazeValidationError(IdeBundle.message("error.name.should.be.specified")));
      return false;
    }

    return TargetName.validate(inputString, errors);
  }
}
