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
package com.google.idea.blaze.android.run.binary;

import com.android.tools.idea.run.ConfigurationSpecificEditor;
import com.android.tools.idea.run.activity.ActivityLocatorUtils;
import com.android.tools.idea.run.util.LaunchUtils;
import com.google.idea.blaze.android.run.binary.instantrun.InstantRunExperiment;
import com.google.idea.blaze.base.ui.IntegerTextField;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Key;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.LanguageTextField;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ResourceBundle;

/**
 * The part of the Blaze Android run configuration editor that allows the user to pick an
 * android_binary target and an activity to launch.
 * Patterned after {@link org.jetbrains.android.run.ApplicationRunParameters}.
 */
class BlazeAndroidBinaryRunConfigurationStateEditor implements ConfigurationSpecificEditor<BlazeAndroidBinaryRunConfiguration> {
  public static final Key<BlazeAndroidBinaryRunConfigurationStateEditor> ACTIVITY_CLASS_TEXT_FIELD_KEY =
    Key.create("BlazeActivityClassTextField");

  @NotNull
  private final Project project;
  @Nullable
  private JPanel panel;
  private ComponentWithBrowseButton<EditorTextField> activityField;
  private JRadioButton launchNothingButton;
  private JRadioButton launchDefaultButton;
  private JRadioButton launchCustomButton;
  private JCheckBox mobileInstallCheckBox;
  private JCheckBox splitApksCheckBox;
  private JCheckBox instantRunCheckBox;
  private IntegerTextField userIdField;

  BlazeAndroidBinaryRunConfigurationStateEditor(@NotNull final Project project) {
    this.project = project;

    setupUI();
    userIdField.setMinValue(0);

    activityField.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (!project.isInitialized()) {
          return;
        }
        // We find all Activity classes in the module for the selected variant (or any of its deps).
        final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
        PsiClass activityBaseClass = facade.findClass(
          AndroidUtils.ACTIVITY_BASE_CLASS_NAME, ProjectScope.getAllScope(project));
        if (activityBaseClass == null) {
          Messages
            .showErrorDialog(panel, AndroidBundle.message("cant.find.activity.class.error"));
          return;
        }
        GlobalSearchScope searchScope = GlobalSearchScope.projectScope(project);
        PsiClass initialSelection = facade.findClass(
          activityField.getChildComponent().getText(), searchScope);
        TreeClassChooser chooser = TreeClassChooserFactory.getInstance(project)
          .createInheritanceClassChooser("Select Activity Class",
                                         searchScope, activityBaseClass,
                                         initialSelection, null);
        chooser.showDialog();
        PsiClass selClass = chooser.getSelected();
        if (selClass != null) {
          // This must be done because Android represents inner static class paths differently than java.
          String qualifiedActivityName = ActivityLocatorUtils.getQualifiedActivityName(selClass);
          activityField.getChildComponent().setText(qualifiedActivityName);
        }
      }
    });
    ActionListener listener = e -> activityField.setEnabled(launchCustomButton.isSelected());
    launchCustomButton.addActionListener(listener);
    launchDefaultButton.addActionListener(listener);
    launchNothingButton.addActionListener(listener);

    instantRunCheckBox.setVisible(InstantRunExperiment.INSTANT_RUN_ENABLED.getValue());

    /**
     * Only one of mobile-install and instant run can be selected at any one time
     */
    mobileInstallCheckBox.addActionListener(e -> {
      if (mobileInstallCheckBox.isSelected()) {
        instantRunCheckBox.setSelected(false);
      }
    });
    instantRunCheckBox.addActionListener(e -> {
      if (instantRunCheckBox.isSelected()) {
        mobileInstallCheckBox.setSelected(false);
      }
    });

    mobileInstallCheckBox.addActionListener(e -> splitApksCheckBox.setVisible(mobileInstallCheckBox.isSelected()));
  }

  @Override
  public void resetFrom(BlazeAndroidBinaryRunConfiguration configuration) {
    BlazeAndroidBinaryRunConfigurationState configState = configuration.getConfigState();
    boolean launchSpecificActivity = configState.MODE.equals(BlazeAndroidBinaryRunConfigurationState.LAUNCH_SPECIFIC_ACTIVITY);
    if (configState.MODE.equals(BlazeAndroidBinaryRunConfigurationState.LAUNCH_DEFAULT_ACTIVITY)) {
      launchDefaultButton.setSelected(true);
    }
    else if (launchSpecificActivity) {
      launchCustomButton.setSelected(true);
    }
    else {
      launchNothingButton.setSelected(true);
    }
    activityField.setEnabled(launchSpecificActivity);
    if (launchSpecificActivity) {
      activityField.getChildComponent().setText(configState.ACTIVITY_CLASS);
    }

    mobileInstallCheckBox.setSelected(configState.isMobileInstall());
    splitApksCheckBox.setSelected(configState.isUseSplitApksIfPossible());
    instantRunCheckBox.setSelected(configState.isInstantRun());

    userIdField.setEnabled(!configState.MODE.equals(BlazeAndroidBinaryRunConfigurationState.DO_NOTHING));
    userIdField.setValue(LaunchUtils.getUserIdFromFlags(configState.ACTIVITY_EXTRA_FLAGS));
    splitApksCheckBox.setVisible(configState.isMobileInstall());
  }

  @Override
  public Component getComponent() {
    return panel;
  }

  @Override
  public void applyTo(BlazeAndroidBinaryRunConfiguration configuration) {
    BlazeAndroidBinaryRunConfigurationState configState = configuration.getConfigState();
    configState.ACTIVITY_EXTRA_FLAGS = getFlagsFromUserId((Number)userIdField.getValue());
    if (launchDefaultButton.isSelected()) {
      configState.MODE = BlazeAndroidBinaryRunConfigurationState.LAUNCH_DEFAULT_ACTIVITY;
    }
    else if (launchCustomButton.isSelected()) {
      configState.MODE = BlazeAndroidBinaryRunConfigurationState.LAUNCH_SPECIFIC_ACTIVITY;
      configState.ACTIVITY_CLASS = activityField.getChildComponent().getText();
    }
    else {
      configState.MODE = BlazeAndroidBinaryRunConfigurationState.DO_NOTHING;
    }
    configState.setMobileInstall(mobileInstallCheckBox.isSelected());
    configState.setUseSplitApksIfPossible(splitApksCheckBox.isSelected());
    configState.setInstantRun(instantRunCheckBox.isSelected());
  }

  @Override
  public JComponent getAnchor() {
    return null;
  }

  @Override
  public void setAnchor(JComponent anchor) {
  }

  private void createUIComponents() {
    final EditorTextField editorTextField = new LanguageTextField(PlainTextLanguage.INSTANCE,
                                                                  project, "") {
      @Override
      protected EditorEx createEditor() {
        final EditorEx editor = super.createEditor();
        final PsiFile file =
          PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());

        if (file != null) {
          DaemonCodeAnalyzer.getInstance(project).setHighlightingEnabled(file, false);
        }
        editor.putUserData(ACTIVITY_CLASS_TEXT_FIELD_KEY, BlazeAndroidBinaryRunConfigurationStateEditor.this);
        return editor;
      }
    };
    activityField = new ComponentWithBrowseButton<EditorTextField>(editorTextField, null);
  }

  @NotNull
  private static String getFlagsFromUserId(@Nullable Number userId) {
    return userId != null ? ("--user " + userId.intValue()) : "";
  }

  /**
   * Initially generated by IntelliJ from a .form file, then checked in as source.
   */
  private void setupUI() {
    createUIComponents();
    panel = new JPanel();
    panel.setLayout(new GridLayoutManager(4, 2, new Insets(0, 0, 0, 0), -1, -1));
    final JPanel panel1 = new JPanel();
    panel1.setLayout(new GridLayoutManager(4, 2, new Insets(0, 0, 0, 0), -1, -1));
    panel.add(panel1, new GridConstraints(3, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                          GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                          GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0,
                                          false));
    panel1.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Activity", TitledBorder.DEFAULT_JUSTIFICATION,
                                                      TitledBorder.DEFAULT_POSITION,
                                                      new Font(panel1.getFont().getName(), panel1.getFont().getStyle(),
                                                               panel1.getFont().getSize()), new Color(-16777216)));
    launchNothingButton = new JRadioButton();
    this.loadButtonText(launchNothingButton,
                              ResourceBundle.getBundle("messages/AndroidBundle").getString("android.run.configuration.do.nothing.label"));
    panel1.add(launchNothingButton, new GridConstraints(0, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                        GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    launchDefaultButton = new JRadioButton();
    launchDefaultButton.setText("Launch default Activity");
    launchDefaultButton.setMnemonic('L');
    launchDefaultButton.setDisplayedMnemonicIndex(0);
    panel1.add(launchDefaultButton, new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                        GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    launchCustomButton = new JRadioButton();
    launchCustomButton.setText("Launch:");
    launchCustomButton.setMnemonic('A');
    launchCustomButton.setDisplayedMnemonicIndex(1);
    panel1.add(launchCustomButton,
               new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                   GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    panel1.add(activityField, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                  GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                  GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final JLabel label1 = new JLabel();
    label1.setText("User ID");
    panel1.add(label1,
               new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                   GridConstraints.SIZEPOLICY_FIXED, null, null, null, 1, false));
    userIdField = new IntegerTextField();
    panel1.add(userIdField, new GridConstraints(3, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                                GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0,
                                                false));
    mobileInstallCheckBox = new JCheckBox();
    mobileInstallCheckBox.setText(" Use blaze mobile-install (go/as-mi)");
    panel.add(mobileInstallCheckBox, new GridConstraints(0, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                         GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                         GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    splitApksCheckBox = new JCheckBox();
    splitApksCheckBox.setText(" Use --split_apks where possible");
    panel.add(splitApksCheckBox, new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                         GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                         GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    instantRunCheckBox = new JCheckBox();
    instantRunCheckBox.setText(" Use InstantRun");
    panel.add(instantRunCheckBox, new GridConstraints(2, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                         GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                         GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    ButtonGroup buttonGroup;
    buttonGroup = new ButtonGroup();
    buttonGroup.add(launchDefaultButton);
    buttonGroup.add(launchCustomButton);
    buttonGroup.add(launchNothingButton);
  }

  /**
   * Initially generated by IntelliJ from a .form file.
   */
  private void loadButtonText(AbstractButton component, String text) {
    StringBuffer result = new StringBuffer();
    boolean haveMnemonic = false;
    char mnemonic = '\0';
    int mnemonicIndex = -1;
    for (int i = 0; i < text.length(); i++) {
      if (text.charAt(i) == '&') {
        i++;
        if (i == text.length()) break;
        if (!haveMnemonic && text.charAt(i) != '&') {
          haveMnemonic = true;
          mnemonic = text.charAt(i);
          mnemonicIndex = result.length();
        }
      }
      result.append(text.charAt(i));
    }
    component.setText(result.toString());
    if (haveMnemonic) {
      component.setMnemonic(mnemonic);
      component.setDisplayedMnemonicIndex(mnemonicIndex);
    }
  }
}
