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
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.bazel.BuildSystemProvider;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeUserSettings;
import com.google.idea.blaze.base.settings.BlazeUserSettings.FocusBehavior;
import com.google.idea.blaze.base.settings.BuildSystem;
import com.google.idea.blaze.base.ui.FileSelectorWithStoredHistory;
import com.google.idea.common.settings.SearchableText;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.JBUI;
import javax.swing.Box;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.SwingConstants;

/** Base blaze settings. */
public class BlazeUserSettingsConfigurable implements UnnamedConfigurable {

  static class UiContributor implements BlazeUserSettingsCompositeConfigurable.UiContributor {
    @Override
    public UnnamedConfigurable getConfigurable() {
      return new BlazeUserSettingsConfigurable();
    }

    @Override
    public ImmutableCollection<SearchableText> getSearchableText() {
      return OPTIONS;
    }
  }

  private static final String BLAZE_BINARY_PATH_KEY = "blaze.binary.path";
  public static final String BAZEL_BINARY_PATH_KEY = "bazel.binary.path";

  private final JPanel panel;

  private final ComboBox<FocusBehavior> showBlazeConsoleOnSync =
      new ComboBox<>(FocusBehavior.values());
  private final ComboBox<FocusBehavior> showProblemsViewOnSync =
      new ComboBox<>(FocusBehavior.values());
  private final ComboBox<FocusBehavior> showBlazeConsoleOnRun =
      new ComboBox<>(FocusBehavior.values());
  private final ComboBox<FocusBehavior> showProblemsViewOnRun =
      new ComboBox<>(FocusBehavior.values());
  private JCheckBox collapseProjectView;
  private JCheckBox formatBuildFilesOnSave;
  private JCheckBox showAddFileToProjectNotification;
  private FileSelectorWithStoredHistory blazeBinaryPathField;
  private FileSelectorWithStoredHistory bazelBinaryPathField;

  private BlazeUserSettingsConfigurable() {
    panel = setupUi();
  }

  @Override
  public void apply() {
    BlazeUserSettings settings = BlazeUserSettings.getInstance();
    settings.setShowBlazeConsoleOnSync((FocusBehavior) showBlazeConsoleOnSync.getSelectedItem());
    settings.setShowProblemsViewOnSync((FocusBehavior) showProblemsViewOnSync.getSelectedItem());
    settings.setShowBlazeConsoleOnRun((FocusBehavior) showBlazeConsoleOnRun.getSelectedItem());
    settings.setShowProblemsViewOnRun((FocusBehavior) showProblemsViewOnRun.getSelectedItem());
    settings.setCollapseProjectView(collapseProjectView.isSelected());
    settings.setFormatBuildFilesOnSave(formatBuildFilesOnSave.isSelected());
    settings.setShowAddFileToProjectNotification(showAddFileToProjectNotification.isSelected());
    settings.setBlazeBinaryPath(Strings.nullToEmpty(blazeBinaryPathField.getText()));
    settings.setBazelBinaryPath(Strings.nullToEmpty(bazelBinaryPathField.getText()));
  }

  @Override
  public void reset() {
    BlazeUserSettings settings = BlazeUserSettings.getInstance();
    showBlazeConsoleOnSync.setSelectedItem(settings.getShowBlazeConsoleOnSync());
    showProblemsViewOnSync.setSelectedItem(settings.getShowProblemsViewOnSync());
    showBlazeConsoleOnRun.setSelectedItem(settings.getShowBlazeConsoleOnRun());
    showProblemsViewOnRun.setSelectedItem(settings.getShowProblemsViewOnRun());
    collapseProjectView.setSelected(settings.getCollapseProjectView());
    formatBuildFilesOnSave.setSelected(settings.getFormatBuildFilesOnSave());
    showAddFileToProjectNotification.setSelected(settings.getShowAddFileToProjectNotification());
    blazeBinaryPathField.setTextWithHistory(settings.getBlazeBinaryPath());
    bazelBinaryPathField.setTextWithHistory(settings.getBazelBinaryPath());
  }

  @Override
  public boolean isModified() {
    BlazeUserSettings settings = BlazeUserSettings.getInstance();
    return showBlazeConsoleOnSync.getSelectedItem() != settings.getShowBlazeConsoleOnSync()
        || showProblemsViewOnSync.getSelectedItem() != settings.getShowProblemsViewOnSync()
        || showBlazeConsoleOnRun.getSelectedItem() != settings.getShowBlazeConsoleOnRun()
        || showProblemsViewOnRun.getSelectedItem() != settings.getShowProblemsViewOnRun()
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
  }

  @Override
  public JComponent createComponent() {
    return panel;
  }

  private static final SearchableText TOOL_WINDOW_POPUP_BEHAVIOR_OPTION =
      SearchableText.withLabel("Tool window popup behavior")
          .addTags("show", "automatic")
          .addTags(Blaze.defaultBuildSystemName(), "console")
          .addTags("problems", "view")
          .build();
  private static final SearchableText SHOW_CONSOLE_OPTION =
      SearchableText.forLabel(String.format("%s Console", Blaze.defaultBuildSystemName()));
  private static final SearchableText SHOW_PROBLEMS_VIEW_OPTION =
      SearchableText.forLabel("Problems View");
  private static final SearchableText SHOW_ON_SYNC_OPTION = SearchableText.forLabel("On Sync:");
  private static final SearchableText SHOW_ON_RUN_OPTION =
      SearchableText.forLabel("For Run/Debug actions:");

  private static final SearchableText COLLAPSE_PROJECT_VIEW_OPTION =
      SearchableText.forLabel("Collapse project view directory roots");
  private static final SearchableText FORMAT_BUILD_FILES_ON_SAVE_OPTION =
      SearchableText.forLabel("Automatically format BUILD/Skylark files on file save");
  public static final SearchableText SHOW_ADD_FILE_TO_PROJECT_OPTION =
      SearchableText.forLabel("Show 'Add source to project' editor notifications");
  private static final SearchableText BLAZE_BINARY_PATH_OPTION =
      SearchableText.forLabel("Blaze binary location");
  private static final SearchableText BAZEL_BINARY_PATH_OPTION =
      SearchableText.forLabel("Bazel binary location");

  private static final ImmutableList<SearchableText> OPTIONS =
      ImmutableList.of(
          TOOL_WINDOW_POPUP_BEHAVIOR_OPTION,
          SHOW_CONSOLE_OPTION,
          SHOW_PROBLEMS_VIEW_OPTION,
          SHOW_ON_SYNC_OPTION,
          SHOW_ON_RUN_OPTION,
          COLLAPSE_PROJECT_VIEW_OPTION,
          FORMAT_BUILD_FILES_ON_SAVE_OPTION,
          SHOW_ADD_FILE_TO_PROJECT_OPTION,
          BLAZE_BINARY_PATH_OPTION,
          BAZEL_BINARY_PATH_OPTION);

  private JPanel setupUi() {
    final int totalRowSize = 8;
    int rowi = 0;

    JPanel panel = new JPanel();
    panel.setLayout(new GridLayoutManager(totalRowSize, 2, JBUI.emptyInsets(), -1, -1));

    panel.add(
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
    panel.add(new JSeparator(SwingConstants.HORIZONTAL), defaultNoGrowConstraints(rowi++, 0, 1, 2));

    collapseProjectView = new JBCheckBox(COLLAPSE_PROJECT_VIEW_OPTION.label());
    collapseProjectView.setSelected(false);
    panel.add(
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
    formatBuildFilesOnSave = new JBCheckBox(FORMAT_BUILD_FILES_ON_SAVE_OPTION.label());
    formatBuildFilesOnSave.setSelected(false);
    panel.add(
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
    showAddFileToProjectNotification = new JBCheckBox(SHOW_ADD_FILE_TO_PROJECT_OPTION.label());
    showAddFileToProjectNotification.setSelected(false);
    panel.add(
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

    blazeBinaryPathField =
        FileSelectorWithStoredHistory.create(
            BLAZE_BINARY_PATH_KEY, "Specify the blaze binary path");
    bazelBinaryPathField =
        FileSelectorWithStoredHistory.create(
            BAZEL_BINARY_PATH_KEY, "Specify the bazel binary path");

    if (BuildSystemProvider.isBuildSystemAvailable(BuildSystem.Blaze)) {
      addBinaryLocationSetting(
          panel, new JBLabel(BLAZE_BINARY_PATH_OPTION.label()), blazeBinaryPathField, rowi++);
    }
    if (BuildSystemProvider.isBuildSystemAvailable(BuildSystem.Bazel)) {
      addBinaryLocationSetting(
          panel, new JBLabel(BAZEL_BINARY_PATH_OPTION.label()), bazelBinaryPathField, rowi++);
    }

    panel.add(
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

    return panel;
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
    panel.setBorder(
        IdeBorderFactory.createTitledBorder(TOOL_WINDOW_POPUP_BEHAVIOR_OPTION.label(), false));
    panel.setLayout(new GridLayoutManager(3, 6, JBUI.emptyInsets(), -1, -1));

    // blaze console settings
    JLabel label = new JBLabel(SHOW_CONSOLE_OPTION.label());
    label.setFont(JBFont.create(label.getFont(), /* tryToScale= */ false).asBold());
    panel.add(label, defaultNoGrowConstraints(0, 0, 1, 3));
    panel.add(new JBLabel(SHOW_ON_SYNC_OPTION.label()), defaultNoGrowConstraints(1, 0, 1, 1));
    panel.add(new JBLabel(SHOW_ON_RUN_OPTION.label()), defaultNoGrowConstraints(2, 0, 1, 1));
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
    label = new JBLabel(SHOW_PROBLEMS_VIEW_OPTION.label());
    label.setFont(JBFont.create(label.getFont(), /* tryToScale= */ false).asBold());
    panel.add(label, defaultNoGrowConstraints(0, 3, 1, 3));
    panel.add(new JBLabel(SHOW_ON_SYNC_OPTION.label()), defaultNoGrowConstraints(1, 3, 1, 1));
    panel.add(new JBLabel(SHOW_ON_RUN_OPTION.label()), defaultNoGrowConstraints(2, 3, 1, 1));
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

  private void addBinaryLocationSetting(
      JPanel panel, JLabel pathLabel, JComponent pathPanel, int rowIndex) {
    pathLabel.setLabelFor(pathPanel);
    panel.add(
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
    panel.add(
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
