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
package com.google.idea.blaze.base.settings.ui;

import com.google.common.base.Objects;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.idea.blaze.base.bazel.BuildSystemProvider;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeUserSettings;
import com.google.idea.blaze.base.settings.BlazeUserSettings.FocusBehavior;
import com.google.idea.blaze.base.settings.BuildSystem;
import com.google.idea.blaze.base.settings.SearchableOptionsHelper;
import com.google.idea.blaze.base.ui.FileSelectorWithStoredHistory;
import com.intellij.openapi.options.BaseConfigurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.JBUI;
import java.util.Collection;
import javax.annotation.Nullable;
import javax.swing.Box;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.SwingConstants;

/** Blaze console view settings */
public class BlazeUserSettingsConfigurable extends BaseConfigurable
    implements SearchableConfigurable {

  private static final String BLAZE_BINARY_PATH_KEY = "blaze.binary.path";
  public static final String BAZEL_BINARY_PATH_KEY = "bazel.binary.path";
  public static final String ID = "blaze.view";
  public static final String SHOW_ADD_FILE_TO_PROJECT_LABEL_TEXT =
      "Show 'Add source to project' editor notifications";

  private final BuildSystem defaultBuildSystem;
  private final Collection<BlazeUserSettingsContributor> settingsContributors;
  private final SearchableOptionsHelper helper;

  private JPanel mainPanel;
  private final ComboBox<FocusBehavior> showBlazeConsoleOnSync =
      new ComboBox<>(FocusBehavior.values());
  private final ComboBox<FocusBehavior> showProblemsViewOnSync =
      new ComboBox<>(FocusBehavior.values());
  private final ComboBox<FocusBehavior> showBlazeConsoleOnRun =
      new ComboBox<>(FocusBehavior.values());
  private final ComboBox<FocusBehavior> showProblemsViewOnRun =
      new ComboBox<>(FocusBehavior.values());
  private JCheckBox resyncAutomatically;
  private JCheckBox collapseProjectView;
  private JCheckBox formatBuildFilesOnSave;
  private JCheckBox showAddFileToProjectNotification;
  private FileSelectorWithStoredHistory blazeBinaryPathField;
  private FileSelectorWithStoredHistory bazelBinaryPathField;

  public BlazeUserSettingsConfigurable() {
    this.defaultBuildSystem = Blaze.defaultBuildSystem();
    this.settingsContributors = Lists.newArrayList();
    this.helper = new SearchableOptionsHelper(this);
    for (BlazeUserSettingsContributor.Provider provider :
        BlazeUserSettingsContributor.Provider.EP_NAME.getExtensions()) {
      settingsContributors.add(provider.getContributor());
    }

    setupUI();
  }

  @Override
  public String getDisplayName() {
    return defaultBuildSystem.getName() + " Settings";
  }

  @Nullable
  @Override
  public String getHelpTopic() {
    return null;
  }

  @Override
  public void apply() {
    BlazeUserSettings settings = BlazeUserSettings.getInstance();
    settings.setShowBlazeConsoleOnSync((FocusBehavior) showBlazeConsoleOnSync.getSelectedItem());
    settings.setShowProblemsViewOnSync((FocusBehavior) showProblemsViewOnSync.getSelectedItem());
    settings.setShowBlazeConsoleOnRun((FocusBehavior) showBlazeConsoleOnRun.getSelectedItem());
    settings.setShowProblemsViewOnRun((FocusBehavior) showProblemsViewOnRun.getSelectedItem());
    settings.setResyncAutomatically(resyncAutomatically.isSelected());
    settings.setCollapseProjectView(collapseProjectView.isSelected());
    settings.setFormatBuildFilesOnSave(formatBuildFilesOnSave.isSelected());
    settings.setShowAddFileToProjectNotification(showAddFileToProjectNotification.isSelected());
    settings.setBlazeBinaryPath(Strings.nullToEmpty(blazeBinaryPathField.getText()));
    settings.setBazelBinaryPath(Strings.nullToEmpty(bazelBinaryPathField.getText()));

    for (BlazeUserSettingsContributor settingsContributor : settingsContributors) {
      settingsContributor.apply();
    }
  }

  @Override
  public void reset() {
    BlazeUserSettings settings = BlazeUserSettings.getInstance();
    showBlazeConsoleOnSync.setSelectedItem(settings.getShowBlazeConsoleOnSync());
    showProblemsViewOnSync.setSelectedItem(settings.getShowProblemsViewOnSync());
    showBlazeConsoleOnRun.setSelectedItem(settings.getShowBlazeConsoleOnRun());
    showProblemsViewOnRun.setSelectedItem(settings.getShowProblemsViewOnRun());
    resyncAutomatically.setSelected(settings.getResyncAutomatically());
    collapseProjectView.setSelected(settings.getCollapseProjectView());
    formatBuildFilesOnSave.setSelected(settings.getFormatBuildFilesOnSave());
    showAddFileToProjectNotification.setSelected(settings.getShowAddFileToProjectNotification());
    blazeBinaryPathField.setTextWithHistory(settings.getBlazeBinaryPath());
    bazelBinaryPathField.setTextWithHistory(settings.getBazelBinaryPath());

    for (BlazeUserSettingsContributor settingsContributor : settingsContributors) {
      settingsContributor.reset();
    }
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    return mainPanel;
  }

  @Override
  public boolean isModified() {
    BlazeUserSettings settings = BlazeUserSettings.getInstance();
    boolean isModified =
        showBlazeConsoleOnSync.getSelectedItem() != settings.getShowBlazeConsoleOnSync()
            || showProblemsViewOnSync.getSelectedItem() != settings.getShowProblemsViewOnSync()
            || showBlazeConsoleOnRun.getSelectedItem() != settings.getShowBlazeConsoleOnRun()
            || showProblemsViewOnRun.getSelectedItem() != settings.getShowProblemsViewOnRun()
            || resyncAutomatically.isSelected() != settings.getResyncAutomatically()
            || collapseProjectView.isSelected() != settings.getCollapseProjectView()
            || formatBuildFilesOnSave.isSelected() != settings.getFormatBuildFilesOnSave()
            || showAddFileToProjectNotification.isSelected()
                != settings.getShowAddFileToProjectNotification()
            || !Objects.equal(
                Strings.nullToEmpty(blazeBinaryPathField.getText()).trim(),
                Strings.nullToEmpty(settings.getBlazeBinaryPath()))
            || !Objects.equal(
                Strings.nullToEmpty(bazelBinaryPathField.getText()).trim(),
                Strings.nullToEmpty(settings.getBazelBinaryPath()));

    for (BlazeUserSettingsContributor settingsContributor : settingsContributors) {
      isModified |= settingsContributor.isModified();
    }
    return isModified;
  }

  @Override
  public void disposeUIResources() {}

  @Override
  public String getId() {
    return ID;
  }

  @Nullable
  @Override
  public Runnable enableSearch(String option) {
    return null;
  }

  /** Initially generated by IntelliJ from a .form file. */
  private void setupUI() {
    int contributorRowCount = 0;
    for (BlazeUserSettingsContributor contributor : settingsContributors) {
      contributorRowCount += contributor.getRowCount();
    }

    final int totalRowSize = 9 + contributorRowCount;
    int rowi = 0;

    SearchableOptionsHelper helper = new SearchableOptionsHelper(this);
    mainPanel = new JPanel();
    mainPanel.setLayout(new GridLayoutManager(totalRowSize, 2, JBUI.emptyInsets(), -1, -1));

    mainPanel.add(
        getFocusBehaviorSettingsUi(),
        new GridConstraints(
            rowi++,
            0,
            1,
            2,
            GridConstraints.ANCHOR_NORTHWEST,
            GridConstraints.FILL_HORIZONTAL,
            GridConstraints.SIZEPOLICY_CAN_GROW,
            GridConstraints.SIZEPOLICY_CAN_GROW,
            null,
            null,
            null,
            0,
            false));
    mainPanel.add(
        new JSeparator(SwingConstants.HORIZONTAL), defaultNoGrowConstraints(rowi++, 0, 1, 2));

    String text = "Automatically re-sync project when BUILD files change";
    helper.registerLabelText(text, true);
    resyncAutomatically = new JCheckBox();
    resyncAutomatically.setSelected(false);
    resyncAutomatically.setText(text);
    mainPanel.add(
        resyncAutomatically,
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
    text = "Collapse project view directory roots";
    helper.registerLabelText(text, true);
    collapseProjectView = new JCheckBox();
    collapseProjectView.setSelected(false);
    collapseProjectView.setText(text);
    mainPanel.add(
        collapseProjectView,
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
    text = "Automatically format BUILD files on file save";
    helper.registerLabelText(text, true);
    formatBuildFilesOnSave = new JCheckBox();
    formatBuildFilesOnSave.setSelected(false);
    formatBuildFilesOnSave.setText(text);
    mainPanel.add(
        formatBuildFilesOnSave,
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
    helper.registerLabelText(SHOW_ADD_FILE_TO_PROJECT_LABEL_TEXT, true);
    showAddFileToProjectNotification = new JCheckBox();
    showAddFileToProjectNotification.setSelected(false);
    showAddFileToProjectNotification.setText(SHOW_ADD_FILE_TO_PROJECT_LABEL_TEXT);
    mainPanel.add(
        showAddFileToProjectNotification,
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
    for (BlazeUserSettingsContributor contributor : settingsContributors) {
      rowi = contributor.addComponents(mainPanel, helper, rowi);
    }

    blazeBinaryPathField =
        FileSelectorWithStoredHistory.create(
            BLAZE_BINARY_PATH_KEY, "Specify the blaze binary path");
    bazelBinaryPathField =
        FileSelectorWithStoredHistory.create(
            BAZEL_BINARY_PATH_KEY, "Specify the bazel binary path");

    if (BuildSystemProvider.isBuildSystemAvailable(BuildSystem.Blaze)) {
      addBinaryLocationSetting(
          helper.createSearchableLabel("Blaze binary location", true),
          blazeBinaryPathField,
          rowi++);
    }
    if (BuildSystemProvider.isBuildSystemAvailable(BuildSystem.Bazel)) {
      addBinaryLocationSetting(
          helper.createSearchableLabel("Bazel binary location", true),
          bazelBinaryPathField,
          rowi++);
    }

    mainPanel.add(
        new Spacer(),
        new GridConstraints(
            rowi,
            0,
            1,
            2,
            GridConstraints.ANCHOR_CENTER,
            GridConstraints.FILL_VERTICAL,
            1,
            GridConstraints.SIZEPOLICY_WANT_GROW,
            null,
            null,
            null,
            0,
            false));
  }

  private static GridConstraints defaultNoGrowConstraints(
      int rowIndex, int columnIndex, int rowSpan, int columnSpan) {
    return new GridConstraints(
        rowIndex,
        columnIndex,
        rowSpan,
        columnSpan,
        GridConstraints.ANCHOR_WEST,
        GridConstraints.FILL_NONE,
        GridConstraints.SIZEPOLICY_FIXED,
        GridConstraints.SIZEPOLICY_FIXED,
        null,
        null,
        null,
        0,
        false);
  }

  private JComponent getFocusBehaviorSettingsUi() {
    JPanel panel = new JPanel();
    panel.setBorder(IdeBorderFactory.createTitledBorder("Tool window popup behavior", false));
    panel.setLayout(new GridLayoutManager(3, 6, JBUI.emptyInsets(), -1, -1));

    // blaze console settings
    JLabel label =
        helper.createSearchableLabel(String.format("%s Console", defaultBuildSystem), true);
    label.setFont(JBFont.create(label.getFont()).asBold());
    panel.add(label, defaultNoGrowConstraints(0, 0, 1, 3));
    panel.add(helper.createSearchableLabel("On Sync:", true), defaultNoGrowConstraints(1, 0, 1, 1));
    panel.add(
        helper.createSearchableLabel("For Run/Debug actions:", true),
        defaultNoGrowConstraints(2, 0, 1, 1));
    panel.add(showBlazeConsoleOnSync, defaultNoGrowConstraints(1, 1, 1, 1));
    panel.add(showBlazeConsoleOnRun, defaultNoGrowConstraints(2, 1, 1, 1));
    panel.add(
        Box.createHorizontalGlue(),
        new GridConstraints(
            1,
            2,
            2,
            1,
            GridConstraints.ANCHOR_WEST,
            GridConstraints.FILL_BOTH,
            GridConstraints.SIZEPOLICY_CAN_GROW,
            GridConstraints.SIZEPOLICY_CAN_GROW,
            null,
            null,
            null,
            0,
            false));

    // problems view settings
    label = helper.createSearchableLabel("Problems View", true);
    label.setFont(JBFont.create(label.getFont()).asBold());
    panel.add(label, defaultNoGrowConstraints(0, 3, 1, 3));
    panel.add(helper.createSearchableLabel("On Sync:", true), defaultNoGrowConstraints(1, 3, 1, 1));
    panel.add(
        helper.createSearchableLabel("For Run/Debug actions:", true),
        defaultNoGrowConstraints(2, 3, 1, 1));
    panel.add(showProblemsViewOnSync, defaultNoGrowConstraints(1, 4, 1, 1));
    panel.add(showProblemsViewOnRun, defaultNoGrowConstraints(2, 4, 1, 1));
    panel.add(
        Box.createHorizontalGlue(),
        new GridConstraints(
            1,
            5,
            2,
            1,
            GridConstraints.ANCHOR_WEST,
            GridConstraints.FILL_BOTH,
            GridConstraints.SIZEPOLICY_CAN_GROW,
            GridConstraints.SIZEPOLICY_CAN_GROW,
            null,
            null,
            null,
            0,
            false));
    return panel;
  }

  private void addBinaryLocationSetting(JLabel pathLabel, JComponent pathPanel, int rowIndex) {
    pathLabel.setLabelFor(pathPanel);
    mainPanel.add(
        pathLabel,
        new GridConstraints(
            rowIndex,
            0,
            1,
            1,
            GridConstraints.ANCHOR_CENTER,
            GridConstraints.FILL_NONE,
            GridConstraints.SIZEPOLICY_FIXED,
            GridConstraints.SIZEPOLICY_FIXED,
            null,
            null,
            null,
            0,
            false));
    mainPanel.add(
        pathPanel,
        new GridConstraints(
            rowIndex,
            1,
            1,
            1,
            GridConstraints.ANCHOR_CENTER,
            GridConstraints.FILL_HORIZONTAL,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            GridConstraints.SIZEPOLICY_FIXED,
            null,
            null,
            null,
            0,
            false));
  }
}
