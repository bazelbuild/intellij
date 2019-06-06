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

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.settings.ui.BlazeUserSettingsCompositeConfigurable;
import com.google.idea.blaze.base.ui.UiUtil;
import com.google.idea.common.settings.SearchableOption;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.components.JBCheckBox;
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
    public UnnamedConfigurable getConfigurable() {
      return new AutoSyncSettingsConfigurable();
    }

    @Override
    public ImmutableCollection<SearchableOption> getSearchableOptions() {
      return ImmutableList.of(
          AUTO_SYNC_OPTION,
          RESYNC_ON_BUILD_FILE_CHANGES_OPTION,
          RESYNC_ON_PROTO_CHANGES_OPTION,
          RESYNC_ON_VCS_SYNC_OPTION);
    }
  }

  private static final SearchableOption AUTO_SYNC_OPTION =
      SearchableOption.forLabel("Auto sync on:");
  private static final SearchableOption RESYNC_ON_BUILD_FILE_CHANGES_OPTION =
      SearchableOption.forLabel("BUILD file changes");
  private static final SearchableOption RESYNC_ON_PROTO_CHANGES_OPTION =
      SearchableOption.forLabel("Proto changes");
  private static final SearchableOption RESYNC_ON_VCS_SYNC_OPTION =
      SearchableOption.withLabel("VCS sync")
          .addTags("Piper", "switch")
          .addTags("Fig", "update", "rebase")
          .build();

  private final JPanel panel;

  private final JCheckBox resyncOnBuildFileChanges;
  private final JCheckBox resyncOnProtoChanges;
  private final JCheckBox resyncOnVcsSync;

  private AutoSyncSettingsConfigurable() {
    resyncOnBuildFileChanges = new JBCheckBox(RESYNC_ON_BUILD_FILE_CHANGES_OPTION.label());
    resyncOnProtoChanges = new JBCheckBox(RESYNC_ON_PROTO_CHANGES_OPTION.label());
    resyncOnVcsSync = new JBCheckBox(RESYNC_ON_VCS_SYNC_OPTION.label());
    panel = setupUi();
  }

  @Override
  public void apply() {
    AutoSyncSettings settings = AutoSyncSettings.getInstance();
    settings.autoSyncOnBuildChanges = resyncOnBuildFileChanges.isSelected();
    settings.autoSyncOnProtoChanges = resyncOnProtoChanges.isSelected();
    settings.autoSyncOnVcsSync = resyncOnVcsSync.isSelected();
  }

  @Override
  public void reset() {
    AutoSyncSettings settings = AutoSyncSettings.getInstance();
    resyncOnBuildFileChanges.setSelected(settings.autoSyncOnBuildChanges);
    resyncOnProtoChanges.setSelected(settings.autoSyncOnProtoChanges);
    resyncOnVcsSync.setSelected(settings.autoSyncOnVcsSync);
  }

  @Override
  public boolean isModified() {
    AutoSyncSettings settings = AutoSyncSettings.getInstance();
    return resyncOnBuildFileChanges.isSelected() != settings.autoSyncOnBuildChanges
        || resyncOnProtoChanges.isSelected() != settings.autoSyncOnProtoChanges
        || resyncOnVcsSync.isSelected() != settings.autoSyncOnVcsSync;
  }

  @Override
  public JComponent createComponent() {
    return panel;
  }

  private JPanel setupUi() {
    JPanel panel = new JPanel(new BorderLayout(0, 10));
    panel.setBorder(
        IdeBorderFactory.createTitledBorder(AUTO_SYNC_OPTION.label(), /* hasIndent= */ true));

    panel.add(
        UiUtil.createBox(resyncOnBuildFileChanges, resyncOnProtoChanges, resyncOnVcsSync),
        BorderLayout.CENTER);

    return panel;
  }
}
