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
package com.google.idea.blaze.java.settings;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.SearchableOptionsHelper;
import com.google.idea.blaze.base.settings.ui.BlazeUserSettingsContributor;
import com.intellij.uiDesigner.core.GridConstraints;
import javax.swing.JCheckBox;
import javax.swing.JPanel;

/** Contributes java-specific settings. */
public class BlazeJavaUserSettingsContributor implements BlazeUserSettingsContributor {
  private JCheckBox useJarCache;
  private final ImmutableList<JCheckBox> components;

  BlazeJavaUserSettingsContributor() {
    useJarCache = new JCheckBox();
    useJarCache.setText(
        String.format(
            "Use a local jar cache. More robust, but we can miss %s changes made outside the IDE.",
            Blaze.defaultBuildSystemName()));

    components = ImmutableList.of(useJarCache);
  }

  @Override
  public void apply() {
    BlazeJavaUserSettings settings = BlazeJavaUserSettings.getInstance();
    settings.setUseJarCache(useJarCache.isSelected());
  }

  @Override
  public void reset() {
    BlazeJavaUserSettings settings = BlazeJavaUserSettings.getInstance();
    useJarCache.setSelected(settings.getUseJarCache());
  }

  @Override
  public boolean isModified() {
    BlazeJavaUserSettings settings = BlazeJavaUserSettings.getInstance();
    return !Objects.equal(useJarCache.isSelected(), settings.getUseJarCache());
  }

  @Override
  public int getRowCount() {
    return components.size();
  }

  @Override
  public int addComponents(JPanel panel, SearchableOptionsHelper helper, int rowi) {
    for (JCheckBox contributedComponent : components) {
      helper.registerLabelText(contributedComponent.getText(), true);
      panel.add(
          contributedComponent,
          new GridConstraints(
              rowi++,
              0,
              1,
              2,
              GridConstraints.ANCHOR_NORTHWEST,
              GridConstraints.FILL_NONE,
              GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
              GridConstraints.SIZEPOLICY_FIXED,
              null,
              null,
              null,
              0,
              false));
    }
    return rowi;
  }

  static class BlazeJavaUserSettingsProvider implements BlazeUserSettingsContributor.Provider {
    @Override
    public BlazeUserSettingsContributor getContributor() {
      return new BlazeJavaUserSettingsContributor();
    }
  }
}
