/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.sync.autosync;

import com.google.idea.blaze.base.settings.SearchableOptionsHelper;
import com.google.idea.blaze.base.settings.ui.BlazeUserSettingsCompositeConfigurable;
import com.google.idea.blaze.base.ui.UiUtil;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.ui.IdeBorderFactory;
import java.awt.BorderLayout;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;

/** Auto sync settings UI. */
public class AutoSyncSettingsConfigurable implements UnnamedConfigurable {

  /**
   * Temporarily included in the general blaze settings tab. Will be eventually moved to a separate
   * auto-sync / project management tab.
   */
  static class UiContributor implements BlazeUserSettingsCompositeConfigurable.UiContributor {
    @Override
    public UnnamedConfigurable getConfigurable(SearchableOptionsHelper helper) {
      return new AutoSyncSettingsConfigurable(helper);
    }
  }

  private final JPanel panel;

  private final JCheckBox resyncOnBuildFileChanges;
  private final JCheckBox resyncOnProtoChanges;

  private AutoSyncSettingsConfigurable(SearchableOptionsHelper helper) {
    resyncOnBuildFileChanges = helper.createSearchableCheckBox("BUILD file changes", true);
    resyncOnProtoChanges = helper.createSearchableCheckBox("Proto changes", true);
    panel = setupUi(helper);
  }

  @Override
  public void apply() {
    AutoSyncSettings settings = AutoSyncSettings.getInstance();
    settings.autoSyncOnBuildChanges = resyncOnBuildFileChanges.isSelected();
    settings.autoSyncOnProtoChanges = resyncOnProtoChanges.isSelected();
  }

  @Override
  public void reset() {
    AutoSyncSettings settings = AutoSyncSettings.getInstance();
    resyncOnBuildFileChanges.setSelected(settings.autoSyncOnBuildChanges);
    resyncOnProtoChanges.setSelected(settings.autoSyncOnProtoChanges);
  }

  @Override
  public boolean isModified() {
    AutoSyncSettings settings = AutoSyncSettings.getInstance();
    return resyncOnBuildFileChanges.isSelected() != settings.autoSyncOnBuildChanges
        || resyncOnProtoChanges.isSelected() != settings.autoSyncOnProtoChanges;
  }

  @Override
  public JComponent createComponent() {
    return panel;
  }

  private JPanel setupUi(SearchableOptionsHelper helper) {
    JPanel panel = new JPanel(new BorderLayout(0, 10));
    helper.registerLabelText("Auto sync", true);
    panel.setBorder(IdeBorderFactory.createTitledBorder("Auto sync on:", /* hasIndent= */ true));

    panel.add(
        UiUtil.createBox(resyncOnBuildFileChanges, resyncOnProtoChanges), BorderLayout.CENTER);

    return panel;
  }
}
