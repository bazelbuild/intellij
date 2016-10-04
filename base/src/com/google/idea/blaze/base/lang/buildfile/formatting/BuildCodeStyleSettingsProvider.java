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
import com.google.idea.blaze.base.lang.buildfile.language.BuildFileType;
import com.intellij.application.options.CodeStyleAbstractConfigurable;
import com.intellij.application.options.CodeStyleAbstractPanel;
import com.intellij.application.options.TabbedLanguageCodeStylePanel;
import com.intellij.openapi.options.Configurable;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsProvider;
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider;
import javax.annotation.Nullable;

/** Separate configurable code-style settings for BUILD language. */
public class BuildCodeStyleSettingsProvider extends CodeStyleSettingsProvider {

  @Override
  public Configurable createSettingsPage(
      CodeStyleSettings settings, CodeStyleSettings originalSettings) {
    return new CodeStyleAbstractConfigurable(
        settings, originalSettings, BuildFileType.INSTANCE.getDescription()) {
      @Override
      protected CodeStyleAbstractPanel createPanel(final CodeStyleSettings settings) {
        return new TabbedLanguageCodeStylePanel(
            BuildFileLanguage.INSTANCE, getCurrentSettings(), settings) {
          @Override
          protected void initTabs(CodeStyleSettings settings) {
            LanguageCodeStyleSettingsProvider provider =
                LanguageCodeStyleSettingsProvider.forLanguage(getDefaultLanguage());
            addIndentOptionsTab(settings);
          }
        };
      }

      @Override
      public String getHelpTopic() {
        return null;
      }
    };
  }

  @Nullable
  @Override
  public String getConfigurableDisplayName() {
    return BuildFileType.INSTANCE.getDescription();
  }
}
