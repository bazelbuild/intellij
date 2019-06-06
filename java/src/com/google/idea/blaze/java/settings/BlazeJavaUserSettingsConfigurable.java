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
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.ui.BlazeUserSettingsCompositeConfigurable;
import com.google.idea.common.settings.SearchableOption;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.util.ui.JBUI;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;

/** Contributes java-specific settings. */
class BlazeJavaUserSettingsConfigurable implements UnnamedConfigurable {

  static class UiContributor implements BlazeUserSettingsCompositeConfigurable.UiContributor {
    @Override
    public UnnamedConfigurable getConfigurable() {
      return new BlazeJavaUserSettingsConfigurable();
    }

    @Override
    public ImmutableCollection<SearchableOption> getSearchableOptions() {
      return ImmutableList.of(USE_JAR_CACHE_OPTION);
    }
  }

  private static final SearchableOption USE_JAR_CACHE_OPTION =
      SearchableOption.forLabel(
          String.format(
              "Use a local jar cache."
                  + " More robust, but we can miss %s changes made outside the IDE.",
              Blaze.defaultBuildSystemName()));

  private final JPanel panel;
  private final JCheckBox useJarCache;
  private final ImmutableList<JCheckBox> components;

  private BlazeJavaUserSettingsConfigurable() {
    useJarCache = new JCheckBox();
    useJarCache.setText(USE_JAR_CACHE_OPTION.label());

    components = ImmutableList.of(useJarCache);
    panel = setupUi();
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
  public JComponent createComponent() {
    return panel;
  }

  private JPanel setupUi() {
    JPanel panel = new JPanel();
    panel.setLayout(new GridLayoutManager(components.size(), 2, JBUI.emptyInsets(), -1, -1));
    for (int i = 0; i < components.size(); i++) {
      JCheckBox component = components.get(i);
      panel.add(
          component,
          new GridConstraints(
              i,
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
    return panel;
  }
}
