/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.idea.blaze.android;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.openapi.project.Project;
import com.intellij.psi.codeStyle.CodeStyleSchemes;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.impl.source.codeStyle.CodeStyleSchemeImpl;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;

/**
 * Helper class for cleaning up after Android Studio initialization. Android Studio changes code
 * insight and style settings on initialization, so we need to revert these before tearing down the
 * integration test fixture to avoid failing because of changed settings.<br>
 * TODO: Remove once we build for a version where post-initialization insight and style settings are
 * used in the settings check.
 */
public final class AndroidTestCleanupHelper {

  public static void cleanUp(Project project) {
    resetCodeInsightSettings();
    resetCodeStyleSettings(project);
  }

  private static void resetCodeInsightSettings() {
    // We can't just use CodeInsightSettings.getState(), because it excludes fields
    // matching the default values, and thus wouldn't change anything when loaded.
    Element codeInsightElement = new Element("state");
    XmlSerializer.serializeInto(new CodeInsightSettings(), codeInsightElement);
    CodeInsightSettings.getInstance().loadState(codeInsightElement);
  }

  private static void resetCodeStyleSettings(Project project) {
    CodeStyleSettingsManager settingsManager = CodeStyleSettingsManager.getInstance(project);
    if (settingsManager.USE_PER_PROJECT_SETTINGS && settingsManager.PER_PROJECT_SETTINGS != null) {
      settingsManager.PER_PROJECT_SETTINGS = new CodeStyleSettings();
    } else {
      ((CodeStyleSchemeImpl)
              CodeStyleSchemes.getInstance()
                  .findPreferredScheme(settingsManager.PREFERRED_PROJECT_CODE_STYLE))
          .setCodeStyleSettings(new CodeStyleSettings());
    }
  }
}
