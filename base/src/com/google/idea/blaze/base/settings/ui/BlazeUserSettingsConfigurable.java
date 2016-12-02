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
import com.google.idea.blaze.base.settings.Blaze.BuildSystem;
import com.google.idea.blaze.base.settings.BlazeUserSettings;
import com.google.idea.blaze.base.ui.FileSelectorWithStoredHistory;
import com.intellij.openapi.options.BaseConfigurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import java.awt.Insets;
import java.util.Collection;
import javax.annotation.Nullable;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

/** Blaze console view settings */
public class BlazeUserSettingsConfigurable extends BaseConfigurable
    implements SearchableConfigurable {

  private static final String BLAZE_BINARY_PATH_KEY = "blaze.binary.path";
  public static final String BAZEL_BINARY_PATH_KEY = "bazel.binary.path";

  private final BuildSystem defaultBuildSystem;
  private final Collection<BlazeUserSettingsContributor> settingsContributors;

  private JPanel myMainPanel;
  private JCheckBox suppressConsoleForRunAction;
  private JCheckBox resyncAutomatically;
  private JCheckBox collapseProjectView;
  private FileSelectorWithStoredHistory blazeBinaryPathField;
  private FileSelectorWithStoredHistory bazelBinaryPathField;

  public BlazeUserSettingsConfigurable() {
    this.defaultBuildSystem = Blaze.defaultBuildSystem();
    this.settingsContributors = Lists.newArrayList();
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
  public void apply() throws ConfigurationException {
    BlazeUserSettings settings = BlazeUserSettings.getInstance();
    settings.setSuppressConsoleForRunAction(suppressConsoleForRunAction.isSelected());
    settings.setResyncAutomatically(resyncAutomatically.isSelected());
    settings.setCollapseProjectView(collapseProjectView.isSelected());
    settings.setBlazeBinaryPath(Strings.nullToEmpty(blazeBinaryPathField.getText()));
    settings.setBazelBinaryPath(Strings.nullToEmpty(bazelBinaryPathField.getText()));

    for (BlazeUserSettingsContributor settingsContributor : settingsContributors) {
      settingsContributor.apply();
    }
  }

  @Override
  public void reset() {
    BlazeUserSettings settings = BlazeUserSettings.getInstance();
    suppressConsoleForRunAction.setSelected(settings.getSuppressConsoleForRunAction());
    resyncAutomatically.setSelected(settings.getResyncAutomatically());
    collapseProjectView.setSelected(settings.getCollapseProjectView());
    blazeBinaryPathField.setTextWithHistory(settings.getBlazeBinaryPath());
    bazelBinaryPathField.setTextWithHistory(settings.getBazelBinaryPath());

    for (BlazeUserSettingsContributor settingsContributor : settingsContributors) {
      settingsContributor.reset();
    }
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    return myMainPanel;
  }

  @Override
  public boolean isModified() {
    BlazeUserSettings settings = BlazeUserSettings.getInstance();
    boolean isModified =
        !Objects.equal(
                suppressConsoleForRunAction.isSelected(), settings.getSuppressConsoleForRunAction())
            || !Objects.equal(resyncAutomatically.isSelected(), settings.getResyncAutomatically())
            || !Objects.equal(collapseProjectView.isSelected(), settings.getCollapseProjectView())
            || !Objects.equal(
                Strings.nullToEmpty(blazeBinaryPathField.getText()),
                Strings.nullToEmpty(settings.getBlazeBinaryPath()))
            || !Objects.equal(
                Strings.nullToEmpty(bazelBinaryPathField.getText()),
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
    return "blaze.view.settings";
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

    final int totalRowSize = 6 + contributorRowCount;
    int rowi = 0;

    myMainPanel = new JPanel();
    myMainPanel.setLayout(new GridLayoutManager(totalRowSize, 2, new Insets(0, 0, 0, 0), -1, -1));
    suppressConsoleForRunAction = new JCheckBox();
    suppressConsoleForRunAction.setText(
        String.format("Suppress %s console for Run/Debug actions", defaultBuildSystem));
    suppressConsoleForRunAction.setVerticalAlignment(SwingConstants.CENTER);
    myMainPanel.add(
        suppressConsoleForRunAction,
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
    resyncAutomatically = new JCheckBox();
    resyncAutomatically.setSelected(false);
    resyncAutomatically.setText("Automatically re-sync project when BUILD files change");
    myMainPanel.add(
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
    collapseProjectView = new JCheckBox();
    collapseProjectView.setSelected(false);
    collapseProjectView.setText("Collapse project view directory roots");
    myMainPanel.add(
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
    for (BlazeUserSettingsContributor contributor : settingsContributors) {
      rowi = contributor.addComponents(myMainPanel, rowi);
    }

    blazeBinaryPathField =
        FileSelectorWithStoredHistory.create(
            BLAZE_BINARY_PATH_KEY, "Specify the blaze binary path");
    bazelBinaryPathField =
        FileSelectorWithStoredHistory.create(
            BAZEL_BINARY_PATH_KEY, "Specify the bazel binary path");

    if (BuildSystemProvider.isBuildSystemAvailable(BuildSystem.Blaze)) {
      addBinaryLocationSetting(new JLabel("Blaze binary location"), blazeBinaryPathField, rowi++);
    }
    if (BuildSystemProvider.isBuildSystemAvailable(BuildSystem.Bazel)) {
      addBinaryLocationSetting(new JLabel("Bazel binary location"), bazelBinaryPathField, rowi++);
    }

    myMainPanel.add(
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

  private void addBinaryLocationSetting(JLabel pathLabel, JComponent pathPanel, int rowIndex) {
    pathLabel.setLabelFor(pathPanel);
    myMainPanel.add(
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
    myMainPanel.add(
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
