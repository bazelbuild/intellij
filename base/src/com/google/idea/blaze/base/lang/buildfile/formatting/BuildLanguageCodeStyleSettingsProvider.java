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
package com.google.idea.blaze.base.lang.buildfile.formatting;

import com.google.idea.blaze.base.lang.buildfile.language.BuildFileLanguage;
import com.intellij.application.options.IndentOptionsEditor;
import com.intellij.lang.Language;
import com.intellij.psi.codeStyle.CodeStyleSettingsCustomizable;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider;
import javax.annotation.Nullable;

/** Allows BUILD language-specific code style settings */
public class BuildLanguageCodeStyleSettingsProvider extends LanguageCodeStyleSettingsProvider {

  @Override
  public Language getLanguage() {
    return BuildFileLanguage.INSTANCE;
  }

  @Override
  public IndentOptionsEditor getIndentOptionsEditor() {
    return new BuildIndentOptionsEditor();
    // TODO(brendandouglas): use upstream API directly, once it's implemented
    // return new SmartIndentOptionsEditor().withDeclarationParameterIndent();
  }

  @Override
  public void customizeSettings(CodeStyleSettingsCustomizable consumer, SettingsType settingsType) {
    super.customizeSettings(consumer, settingsType);
  }

  @Override
  public String getCodeSample(SettingsType settingsType) {
    return "";
  }

  @Nullable
  @Override
  public CommonCodeStyleSettings getDefaultCommonSettings() {
    CommonCodeStyleSettings defaultSettings =
        new CommonCodeStyleSettings(BuildFileLanguage.INSTANCE);
    CommonCodeStyleSettings.IndentOptions indentOptions = defaultSettings.initIndentOptions();
    indentOptions.TAB_SIZE = 4;
    indentOptions.INDENT_SIZE = 4;
    indentOptions.CONTINUATION_INDENT_SIZE = 4;
    // TODO(brendandouglas): use upstream API directly, once it's implemented
    // indentOptions.DECLARATION_PARAMETER_INDENT = 8;
    return defaultSettings;
  }
}
