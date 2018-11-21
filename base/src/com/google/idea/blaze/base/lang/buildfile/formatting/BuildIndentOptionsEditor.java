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
package com.google.idea.blaze.base.lang.buildfile.formatting;

import com.google.idea.blaze.base.ui.IntegerTextField;
import com.intellij.application.options.SmartIndentOptionsEditor;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings.IndentOptions;
import javax.swing.JLabel;

/**
 * A custom {@link SmartIndentOptionsEditor} which allows for a separate continuation indent for
 * function declaration parameters.
 */
class BuildIndentOptionsEditor extends SmartIndentOptionsEditor {

  private static final String PARAMETER_INDENT_TEXT = "Declaration parameter indent:";

  private final IntegerTextField parameterIndent;

  BuildIndentOptionsEditor() {
    super();
    parameterIndent = new IntegerTextField().setMinValue(0).setMaxValue(32);
  }

  @Override
  protected void addComponents() {
    super.addComponents();
    add(new JLabel(PARAMETER_INDENT_TEXT), parameterIndent);
  }

  @Override
  public boolean isModified(CodeStyleSettings settings, IndentOptions options) {
    return super.isModified(settings, options)
        || isFieldModified(parameterIndent, getCustomSettings(settings).declarationParameterIndent);
  }

  @Override
  public void apply(CodeStyleSettings settings, IndentOptions options) {
    super.apply(settings, options);
    Integer value = parameterIndent.getIntValue();
    if (value != null) {
      getCustomSettings(settings).declarationParameterIndent = value;
    }
  }

  @Override
  public void reset(CodeStyleSettings settings, IndentOptions options) {
    super.reset(settings, options);
    parameterIndent.setValue(getCustomSettings(settings).declarationParameterIndent);
  }

  @Override
  public void setEnabled(boolean enabled) {
    super.setEnabled(enabled);
    parameterIndent.setEnabled(enabled);
  }

  private static BuildCodeStyleSettings getCustomSettings(CodeStyleSettings settings) {
    return settings.getCustomSettings(BuildCodeStyleSettings.class);
  }
}
