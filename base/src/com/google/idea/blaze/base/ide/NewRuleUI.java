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

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Kind.Provider;
import com.google.idea.blaze.base.model.primitives.TargetName;
import com.google.idea.blaze.base.ui.UiUtil;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Set;
import javax.annotation.Nullable;
import javax.swing.JPanel;
import javax.swing.event.DocumentEvent;

final class NewRuleUI {

  private static final ImmutableSet<Kind> HANDLED_RULES =
      Arrays.stream(Provider.EP_NAME.getExtensions())
          .map(Provider::getTargetKinds)
          .flatMap(Set::stream)
          .sorted(Comparator.comparing(Kind::getKindString))
          .collect(toImmutableSet());

  private static final String LAST_SELECTED_KIND = "Blaze.Rule.Kind";

  private final ComboBox<Kind> ruleComboBox;
  private final JBLabel ruleNameLabel = new JBLabel("Rule name:");
  private final JBTextField ruleNameField;

  private boolean ruleNameEditedByUser = false;

  public NewRuleUI(int textFieldLength) {
    this.ruleComboBox = new ComboBox<>(HANDLED_RULES.toArray(new Kind[0]));
    this.ruleNameField = new JBTextField(textFieldLength);
    Kind lastValue =
        Kind.fromRuleName(PropertiesComponent.getInstance().getValue(LAST_SELECTED_KIND));
    if (lastValue != null && HANDLED_RULES.contains(lastValue)) {
      ruleComboBox.setSelectedItem(lastValue);
    }
  }

  public void fillUI(JPanel component, int indentLevel) {
    component.add(ruleNameLabel);
    component.add(ruleNameField, UiUtil.getFillLineConstraints(indentLevel));
    component.add(ruleComboBox, UiUtil.getFillLineConstraints(indentLevel));
  }

  public Kind getSelectedRuleKind() {
    Kind kind = (Kind) ruleComboBox.getSelectedItem();
    PropertiesComponent.getInstance().setValue(LAST_SELECTED_KIND, kind.toString());
    return kind;
  }

  public TargetName getRuleName() {
    return TargetName.create(ruleNameField.getText());
  }

  void syncRuleNameTo(JBTextField textField) {
    ruleNameField
        .getDocument()
        .addDocumentListener(
            new DocumentAdapter() {
              @Override
              protected void textChanged(DocumentEvent e) {
                ruleNameEditedByUser = true;
              }
            });

    textField
        .getDocument()
        .addDocumentListener(
            new DocumentAdapter() {
              @Override
              protected void textChanged(DocumentEvent e) {
                if (!ruleNameEditedByUser) {
                  syncRuleName(textField.getText());
                }
              }
            });
  }

  private void syncRuleName(String text) {
    ruleNameField.setText(text);
    // setText triggers an event which flips the field, so we'll set it back to false
    this.ruleNameEditedByUser = false;
  }

  @Nullable
  public ValidationInfo validate() {
    if (ruleComboBox.getSelectedItem() == null) {
      return new ValidationInfo("Select a rule type", ruleComboBox);
    }
    String ruleName = ruleNameField.getText();
    String error = validateRuleName(ruleName);
    if (error != null) {
      return new ValidationInfo(error, ruleNameField);
    }
    return null;
  }

  @Nullable
  private static String validateRuleName(String inputString) {
    if (inputString.length() == 0) {
      return IdeBundle.message("error.name.should.be.specified");
    }
    return TargetName.validate(inputString);
  }
}
