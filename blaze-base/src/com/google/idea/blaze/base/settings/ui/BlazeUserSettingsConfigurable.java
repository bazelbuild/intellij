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
import com.google.idea.blaze.base.lang.buildfile.language.BuildFileLanguage;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.Blaze.BuildSystem;
import com.google.idea.blaze.base.settings.BlazeUserSettings;
import com.google.idea.blaze.base.sync.status.BlazeSyncStatusImpl;
import com.google.idea.blaze.base.ui.FileSelectorWithStoredHistory;
import com.intellij.openapi.options.BaseConfigurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;

import javax.annotation.Nullable;
import javax.swing.*;
import java.awt.*;

/**
 * Blaze console view settings
 */
public class BlazeUserSettingsConfigurable extends BaseConfigurable implements SearchableConfigurable {

  private static final String BLAZE_BINARY_PATH_KEY = "blaze.binary.path";
  public static final String BAZEL_BINARY_PATH_KEY = "bazel.binary.path";

  private final BuildSystem buildSystem;

  private JPanel myMainPanel;
  private JCheckBox suppressConsoleForRunAction;
  private JCheckBox resyncAutomatically;
  private JCheckBox buildFileSupportEnabled;
  private JCheckBox attachSourcesByDefault;
  private JCheckBox attachSourcesOnDemand;
  private JCheckBox collapseProjectView;
  private FileSelectorWithStoredHistory blazeBinaryPathField;
  private FileSelectorWithStoredHistory bazelBinaryPathField;

  public BlazeUserSettingsConfigurable(Project project) {
    this.buildSystem = Blaze.getBuildSystem(project);
    setupUI();
  }

  @Override
  public String getDisplayName() {
    return buildSystem.getName() + " View Settings";
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
    settings.setBuildFileSupportEnabled(buildFileSupportEnabled.isSelected());
    settings.setAttachSourcesByDefault(attachSourcesByDefault.isSelected());
    settings.setAttachSourcesOnDemand(attachSourcesOnDemand.isSelected());
    settings.setCollapseProjectView(collapseProjectView.isSelected());
    if (blazeBinaryPathField.getText() != null) {
      settings.setBlazeBinaryPath(blazeBinaryPathField.getText());
    }
    if (bazelBinaryPathField.getText() != null) {
      settings.setBazelBinaryPath(bazelBinaryPathField.getText());
    }
  }

  @Override
  public void reset() {
    BlazeUserSettings settings = BlazeUserSettings.getInstance();
    suppressConsoleForRunAction.setSelected(settings.getSuppressConsoleForRunAction());
    resyncAutomatically.setSelected(settings.getResyncAutomatically());
    buildFileSupportEnabled.setSelected(settings.getBuildFileSupportEnabled());
    attachSourcesByDefault.setSelected(settings.getAttachSourcesByDefault());
    attachSourcesOnDemand.setSelected(settings.getAttachSourcesOnDemand());
    collapseProjectView.setSelected(settings.getCollapseProjectView());
    blazeBinaryPathField.setTextWithHistory(settings.getBlazeBinaryPath());
    bazelBinaryPathField.setTextWithHistory(settings.getBazelBinaryPath());
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    resyncAutomatically.setVisible(BlazeSyncStatusImpl.AUTOMATIC_INCREMENTAL_SYNC.getValue());
    buildFileSupportEnabled.setVisible(BuildFileLanguage.BUILD_FILE_SUPPORT_ENABLED.getValue());
    return myMainPanel;
  }

  @Override
  public boolean isModified() {
    BlazeUserSettings settings = BlazeUserSettings.getInstance();
    return !Objects.equal(suppressConsoleForRunAction.isSelected(), settings.getSuppressConsoleForRunAction()) ||
           !Objects.equal(resyncAutomatically.isSelected(), settings.getResyncAutomatically()) ||
           !Objects.equal(buildFileSupportEnabled.isSelected(), settings.getBuildFileSupportEnabled()) ||
           !Objects.equal(attachSourcesByDefault.isSelected(), settings.getAttachSourcesByDefault()) ||
           !Objects.equal(attachSourcesOnDemand.isSelected(), settings.getAttachSourcesOnDemand()) ||
           !Objects.equal(collapseProjectView.isSelected(), settings.getCollapseProjectView()) ||
           !Objects.equal(blazeBinaryPathField.getText(), settings.getBlazeBinaryPath()) ||
           !Objects.equal(bazelBinaryPathField.getText(), settings.getBazelBinaryPath());
  }

  @Override
  public void disposeUIResources() {
  }

  @Override
  public String getId() {
    return "blaze.view.settings";
  }

  @Nullable
  @Override
  public Runnable enableSearch(String option) {
    return null;
  }


  /**
   * Initially generated by IntelliJ from a .form file.
   */
  private void setupUI() {
    myMainPanel = new JPanel();
    myMainPanel.setLayout(new GridLayoutManager(8, 2, new Insets(0, 0, 0, 0), -1, -1));
    suppressConsoleForRunAction = new JCheckBox();
    suppressConsoleForRunAction.setText(String.format("Suppress %s console for Run/Debug actions", buildSystem));
    suppressConsoleForRunAction.setVerticalAlignment(0);
    myMainPanel.add(suppressConsoleForRunAction,
                    new GridConstraints(0, 0, 1, 2, GridConstraints.ANCHOR_NORTHWEST, GridConstraints.FILL_NONE,
                                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                        GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    resyncAutomatically = new JCheckBox();
    resyncAutomatically.setSelected(false);
    resyncAutomatically.setText("Automatically re-sync project when BUILD files change");
    myMainPanel.add(resyncAutomatically, new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_NORTHWEST, GridConstraints.FILL_NONE,
                                                             GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                             GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    buildFileSupportEnabled = new JCheckBox();
    buildFileSupportEnabled.setSelected(true);
    buildFileSupportEnabled.setText("BUILD file language support enabled");
    myMainPanel.add(buildFileSupportEnabled, new GridConstraints(2, 0, 1, 2, GridConstraints.ANCHOR_NORTHWEST, GridConstraints.FILL_NONE,
                                                             GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                             GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    attachSourcesByDefault = new JCheckBox();
    attachSourcesByDefault.setSelected(false);
    attachSourcesByDefault.setText("Automatically attach sources on project sync (WARNING: increases index time by 100%+)");
    myMainPanel.add(attachSourcesByDefault, new GridConstraints(3, 0, 1, 2, GridConstraints.ANCHOR_NORTHWEST, GridConstraints.FILL_NONE,
                                                                GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                                GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    attachSourcesByDefault.addActionListener((event) -> {
      BlazeUserSettings settings = BlazeUserSettings.getInstance();
      if (attachSourcesByDefault.isSelected() && !settings.getAttachSourcesByDefault()) {
        int result = Messages.showOkCancelDialog(
          "You are turning on source jars by default. This setting increases indexing time by "
          + ">100%, can cost ~1GB RAM, and will increase project reopen time significantly. "
          + "Are you sure you want to proceed?",
          "Turn On Sources By Default?",
          null
        );
        if (result != Messages.OK) {
          attachSourcesByDefault.setSelected(false);
        }
      }
    });

    attachSourcesOnDemand = new JCheckBox();
    attachSourcesOnDemand.setSelected(false);
    attachSourcesOnDemand.setText("Automatically attach sources when you open decompiled source");
    myMainPanel.add(attachSourcesOnDemand, new GridConstraints(4, 0, 1, 2, GridConstraints.ANCHOR_NORTHWEST, GridConstraints.FILL_NONE,
                                                                GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                                GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    collapseProjectView = new JCheckBox();
    collapseProjectView.setSelected(false);
    collapseProjectView.setText("Collapse project view directory roots");
    myMainPanel.add(collapseProjectView, new GridConstraints(5, 0, 1, 2, GridConstraints.ANCHOR_NORTHWEST, GridConstraints.FILL_NONE,
                                                               GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                               GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));

    blazeBinaryPathField = FileSelectorWithStoredHistory.create(BLAZE_BINARY_PATH_KEY, "Specify the blaze binary path");
    bazelBinaryPathField = FileSelectorWithStoredHistory.create(BAZEL_BINARY_PATH_KEY, "Specify the bazel binary path");

    JLabel pathLabel;
    JComponent pathPanel;
    if (buildSystem == BuildSystem.Blaze) {
      pathPanel = blazeBinaryPathField;
      pathLabel = new JLabel("Blaze binary location");
    } else {
      pathPanel = bazelBinaryPathField;
      pathLabel = new JLabel("Bazel binary location");
    }
    pathLabel.setLabelFor(pathPanel);
    myMainPanel.add(pathLabel, new GridConstraints(6, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE,
                                                   GridConstraints.SIZEPOLICY_FIXED,
                                                   GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myMainPanel.add(pathPanel, new GridConstraints(6, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                   GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                   GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));

    myMainPanel.add(new Spacer(), new GridConstraints(7, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                                 GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
  }

}
