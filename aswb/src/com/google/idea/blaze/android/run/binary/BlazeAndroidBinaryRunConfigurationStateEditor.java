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
package com.google.idea.blaze.android.run.binary;

import static com.google.idea.blaze.android.run.binary.BlazeAndroidBinaryRunConfigurationHandler.MI_NEVER_ASK_AGAIN;

import com.android.tools.idea.run.activity.ActivityLocatorUtils;
import com.android.tools.idea.run.editor.AndroidProfilersPanel;
import com.android.tools.idea.run.editor.AndroidProfilersPanelCompat;
import com.google.idea.blaze.base.run.state.RunConfigurationState;
import com.google.idea.blaze.base.run.state.RunConfigurationStateEditor;
import com.google.idea.blaze.base.ui.IntegerTextField;
import com.google.idea.blaze.base.ui.UiUtil;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.ide.util.PropertiesComponent;
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
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import java.awt.Color;
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ResourceBundle;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.border.TitledBorder;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidUtils;

/**
 * The part of the Blaze Android Binary handler editor that allows the user to pick an activity to
 * launch. Patterned after {@link org.jetbrains.android.run.ApplicationRunParameters}.
 */
class BlazeAndroidBinaryRunConfigurationStateEditor implements RunConfigurationStateEditor {
  public static final Key<BlazeAndroidBinaryRunConfigurationStateEditor>
      ACTIVITY_CLASS_TEXT_FIELD_KEY = Key.create("BlazeActivityClassTextField");

  private final RunConfigurationStateEditor commonStateEditor;
  private final AndroidProfilersPanel profilersPanel;

  private JPanel panel;
  private ComponentWithBrowseButton<EditorTextField> activityField;
  private JRadioButton launchNothingButton;
  private JRadioButton launchDefaultButton;
  private JRadioButton launchCustomButton;
  private JCheckBox useMobileInstallCheckBox;
  private JCheckBox useWorkProfileIfPresentCheckBox;
  private JCheckBox showLogcatAutomaticallyCheckBox;
  private JLabel userIdLabel;
  private IntegerTextField userIdField;

  private boolean componentEnabled = true;

  BlazeAndroidBinaryRunConfigurationStateEditor(
      RunConfigurationStateEditor commonStateEditor,
      AndroidProfilersPanel profilersPanel,
      Project project) {
    this.commonStateEditor = commonStateEditor;
    this.profilersPanel = profilersPanel;
    setupUI(project);
    userIdField.setMinValue(0);

    activityField.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            if (!project.isInitialized()) {
              return;
            }
            // We find all Activity classes in the module for the selected variant
            // (or any of its deps).
            final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
            PsiClass activityBaseClass =
                facade.findClass(
                    AndroidUtils.ACTIVITY_BASE_CLASS_NAME, ProjectScope.getAllScope(project));
            if (activityBaseClass == null) {
              Messages.showErrorDialog(
                  panel, AndroidBundle.message("cant.find.activity.class.error"));
              return;
            }
            GlobalSearchScope searchScope = GlobalSearchScope.projectScope(project);
            PsiClass initialSelection =
                facade.findClass(activityField.getChildComponent().getText(), searchScope);
            TreeClassChooser chooser =
                TreeClassChooserFactory.getInstance(project)
                    .createInheritanceClassChooser(
                        "Select Activity Class",
                        searchScope,
                        activityBaseClass,
                        initialSelection,
                        null);
            chooser.showDialog();
            PsiClass selClass = chooser.getSelected();
            if (selClass != null) {
              // This must be done because Android represents
              // inner static class paths differently than java.
              String qualifiedActivityName =
                  ActivityLocatorUtils.getQualifiedActivityName(selClass);
              activityField.getChildComponent().setText(qualifiedActivityName);
            }
          }
        });
    ActionListener listener = e -> updateEnabledState();
    launchCustomButton.addActionListener(listener);
    launchDefaultButton.addActionListener(listener);
    launchNothingButton.addActionListener(listener);

    useMobileInstallCheckBox.addActionListener(
        e -> PropertiesComponent.getInstance(project).setValue(MI_NEVER_ASK_AGAIN, true));

    useWorkProfileIfPresentCheckBox.addActionListener(listener);
    showLogcatAutomaticallyCheckBox.addActionListener(listener);
  }

  @Override
  public void resetEditorFrom(RunConfigurationState genericState) {
    BlazeAndroidBinaryRunConfigurationState state =
        (BlazeAndroidBinaryRunConfigurationState) genericState;
    commonStateEditor.resetEditorFrom(state.getCommonState());
    AndroidProfilersPanelCompat.resetFrom(profilersPanel, state.getProfilerState());
    boolean launchSpecificActivity =
        state.getMode().equals(BlazeAndroidBinaryRunConfigurationState.LAUNCH_SPECIFIC_ACTIVITY);
    if (state.getMode().equals(BlazeAndroidBinaryRunConfigurationState.LAUNCH_DEFAULT_ACTIVITY)) {
      launchDefaultButton.setSelected(true);
    } else if (launchSpecificActivity) {
      launchCustomButton.setSelected(true);
    } else {
      launchNothingButton.setSelected(true);
    }
    if (launchSpecificActivity) {
      activityField.getChildComponent().setText(state.getActivityClass());
    }

    useMobileInstallCheckBox.setSelected(
        AndroidBinaryLaunchMethodsUtils.useMobileInstall(
            ((BlazeAndroidBinaryRunConfigurationState) genericState).getLaunchMethod()));
    useWorkProfileIfPresentCheckBox.setSelected(state.useWorkProfileIfPresent());
    userIdField.setValue(state.getUserId());

    showLogcatAutomaticallyCheckBox.setSelected(state.showLogcatAutomatically());

    updateEnabledState();
  }

  @Override
  public void applyEditorTo(RunConfigurationState genericState) {
    BlazeAndroidBinaryRunConfigurationState state =
        (BlazeAndroidBinaryRunConfigurationState) genericState;
    commonStateEditor.applyEditorTo(state.getCommonState());
    AndroidProfilersPanelCompat.applyTo(profilersPanel, state.getProfilerState());

    state.setUserId((Integer) userIdField.getValue());
    if (launchDefaultButton.isSelected()) {
      state.setMode(BlazeAndroidBinaryRunConfigurationState.LAUNCH_DEFAULT_ACTIVITY);
    } else if (launchCustomButton.isSelected()) {
      state.setMode(BlazeAndroidBinaryRunConfigurationState.LAUNCH_SPECIFIC_ACTIVITY);
      state.setActivityClass(activityField.getChildComponent().getText());
    } else {
      state.setMode(BlazeAndroidBinaryRunConfigurationState.DO_NOTHING);
    }
    state.setLaunchMethod(
        AndroidBinaryLaunchMethodsUtils.getLaunchMethod(useMobileInstallCheckBox.isSelected()));
    state.setUseWorkProfileIfPresent(useWorkProfileIfPresentCheckBox.isSelected());
    state.setShowLogcatAutomatically(showLogcatAutomaticallyCheckBox.isSelected());
  }

  @Override
  public JComponent createComponent() {
    JBTabbedPane tabbedPane = new JBTabbedPane();
    JComponent commonStatePane = UiUtil.createBox(commonStateEditor.createComponent(), panel);
    commonStatePane.setOpaque(true);
    tabbedPane.addTab("General", commonStatePane);
    tabbedPane.addTab("Profiler", profilersPanel.getComponent());
    return UiUtil.createBox(tabbedPane);
  }

  private void updateEnabledState() {
    boolean useWorkProfile = useWorkProfileIfPresentCheckBox.isSelected();
    userIdLabel.setEnabled(componentEnabled && !useWorkProfile);
    userIdField.setEnabled(componentEnabled && !useWorkProfile);
    commonStateEditor.setComponentEnabled(componentEnabled);
    activityField.setEnabled(componentEnabled && launchCustomButton.isSelected());
    launchNothingButton.setEnabled(componentEnabled);
    launchDefaultButton.setEnabled(componentEnabled);
    launchCustomButton.setEnabled(componentEnabled);
    useMobileInstallCheckBox.setEnabled(componentEnabled);
    useWorkProfileIfPresentCheckBox.setEnabled(componentEnabled);
    showLogcatAutomaticallyCheckBox.setEnabled(componentEnabled);
  }

  @Override
  public void setComponentEnabled(boolean enabled) {
    componentEnabled = enabled;
    updateEnabledState();
  }

  private void createUIComponents(Project project) {
    final EditorTextField editorTextField =
        new LanguageTextField(PlainTextLanguage.INSTANCE, project, "") {
          @Override
          protected EditorEx createEditor() {
            final EditorEx editor = super.createEditor();
            final PsiFile file =
                PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());

            if (file != null) {
              DaemonCodeAnalyzer.getInstance(project).setHighlightingEnabled(file, false);
            }
            editor.putUserData(
                ACTIVITY_CLASS_TEXT_FIELD_KEY, BlazeAndroidBinaryRunConfigurationStateEditor.this);
            return editor;
          }
        };
    activityField = new ComponentWithBrowseButton<EditorTextField>(editorTextField, null);
  }

  /** Initially generated by IntelliJ from a .form file, then checked in as source. */
  private void setupUI(Project project) {
    createUIComponents(project);
    panel = new JPanel();
    panel.setLayout(new GridLayoutManager(6, 2, new Insets(0, 0, 0, 0), -1, -1));
    final JPanel activityPanel = new JPanel();
    activityPanel.setLayout(new GridLayoutManager(4, 2, new Insets(0, 0, 0, 0), -1, -1));
    panel.add(
        activityPanel,
        new GridConstraints(
            3,
            0,
            1,
            2,
            GridConstraints.ANCHOR_CENTER,
            GridConstraints.FILL_BOTH,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            null,
            null,
            null,
            0,
            false));
    activityPanel.setBorder(
        BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(),
            "Activity",
            TitledBorder.DEFAULT_JUSTIFICATION,
            TitledBorder.DEFAULT_POSITION,
            new Font(
                activityPanel.getFont().getName(),
                activityPanel.getFont().getStyle(),
                activityPanel.getFont().getSize()),
            new Color(-16777216)));
    final JPanel userPanel = new JPanel();
    userPanel.setLayout(new GridLayoutManager(2, 2, new Insets(0, 0, 0, 0), -1, -1));
    panel.add(
        userPanel,
        new GridConstraints(
            4,
            0,
            1,
            2,
            GridConstraints.ANCHOR_CENTER,
            GridConstraints.FILL_BOTH,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            null,
            null,
            null,
            0,
            false));
    userPanel.setBorder(
        BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(),
            "User",
            TitledBorder.DEFAULT_JUSTIFICATION,
            TitledBorder.DEFAULT_POSITION,
            new Font(
                userPanel.getFont().getName(),
                userPanel.getFont().getStyle(),
                userPanel.getFont().getSize()),
            new Color(-16777216)));
    final JPanel logcatPanel = new JPanel();
    logcatPanel.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
    panel.add(
        logcatPanel,
        new GridConstraints(
            5,
            0,
            1,
            2,
            GridConstraints.ANCHOR_CENTER,
            GridConstraints.FILL_BOTH,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            null,
            null,
            null,
            0,
            false));
    logcatPanel.setBorder(
        BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(),
            "Logcat",
            TitledBorder.DEFAULT_JUSTIFICATION,
            TitledBorder.DEFAULT_POSITION,
            new Font(
                logcatPanel.getFont().getName(),
                logcatPanel.getFont().getStyle(),
                logcatPanel.getFont().getSize()),
            Color.BLACK));
    launchNothingButton = new JRadioButton();
    this.loadButtonText(
        launchNothingButton,
        ResourceBundle.getBundle("messages/AndroidBundle")
            .getString("android.run.configuration.do.nothing.label"));
    activityPanel.add(
        launchNothingButton,
        new GridConstraints(
            0,
            0,
            1,
            2,
            GridConstraints.ANCHOR_CENTER,
            GridConstraints.FILL_HORIZONTAL,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            GridConstraints.SIZEPOLICY_FIXED,
            null,
            null,
            null,
            0,
            false));
    launchDefaultButton = new JRadioButton();
    launchDefaultButton.setText("Launch default Activity");
    launchDefaultButton.setMnemonic('L');
    launchDefaultButton.setDisplayedMnemonicIndex(0);
    activityPanel.add(
        launchDefaultButton,
        new GridConstraints(
            1,
            0,
            1,
            2,
            GridConstraints.ANCHOR_CENTER,
            GridConstraints.FILL_HORIZONTAL,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            GridConstraints.SIZEPOLICY_FIXED,
            null,
            null,
            null,
            0,
            false));
    launchCustomButton = new JRadioButton();
    launchCustomButton.setText("Launch:");
    launchCustomButton.setMnemonic('A');
    launchCustomButton.setDisplayedMnemonicIndex(1);
    activityPanel.add(
        launchCustomButton,
        new GridConstraints(
            2,
            0,
            1,
            1,
            GridConstraints.ANCHOR_WEST,
            GridConstraints.FILL_NONE,
            GridConstraints.SIZEPOLICY_FIXED,
            GridConstraints.SIZEPOLICY_FIXED,
            null,
            null,
            null,
            0,
            false));
    activityPanel.add(
        activityField,
        new GridConstraints(
            2,
            1,
            1,
            1,
            GridConstraints.ANCHOR_CENTER,
            GridConstraints.FILL_BOTH,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            GridConstraints.SIZEPOLICY_FIXED,
            null,
            null,
            null,
            0,
            false));
    useWorkProfileIfPresentCheckBox = new JCheckBox();
    useWorkProfileIfPresentCheckBox.setText(" Use work profile if present");
    userPanel.add(
        useWorkProfileIfPresentCheckBox,
        new GridConstraints(
            0,
            0,
            1,
            2,
            GridConstraints.ANCHOR_WEST,
            GridConstraints.FILL_NONE,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            GridConstraints.SIZEPOLICY_FIXED,
            null,
            null,
            null,
            0,
            false));
    userIdLabel = new JLabel();
    userIdLabel.setText("User ID");
    userPanel.add(
        userIdLabel,
        new GridConstraints(
            1,
            0,
            1,
            1,
            GridConstraints.ANCHOR_WEST,
            GridConstraints.FILL_NONE,
            GridConstraints.SIZEPOLICY_FIXED,
            GridConstraints.SIZEPOLICY_FIXED,
            null,
            null,
            null,
            1,
            false));
    userIdField = new IntegerTextField();
    userPanel.add(
        userIdField,
        new GridConstraints(
            1,
            1,
            1,
            1,
            GridConstraints.ANCHOR_WEST,
            GridConstraints.FILL_HORIZONTAL,
            GridConstraints.SIZEPOLICY_WANT_GROW,
            GridConstraints.SIZEPOLICY_FIXED,
            null,
            null,
            null,
            0,
            false));
    showLogcatAutomaticallyCheckBox = new JCheckBox(" Show logcat automatically");
    logcatPanel.add(
        showLogcatAutomaticallyCheckBox,
        new GridConstraints(
            0,
            0,
            1,
            1,
            GridConstraints.ANCHOR_WEST,
            GridConstraints.FILL_HORIZONTAL,
            GridConstraints.SIZEPOLICY_WANT_GROW,
            GridConstraints.SIZEPOLICY_FIXED,
            null,
            null,
            null,
            0,
            false));
    useMobileInstallCheckBox = new JCheckBox();
    useMobileInstallCheckBox.setText("Use mobile-install");
    useMobileInstallCheckBox.setSelected(true);
    panel.add(
        useMobileInstallCheckBox,
        new GridConstraints(
            0,
            0,
            1,
            2,
            GridConstraints.ANCHOR_WEST,
            GridConstraints.FILL_NONE,
            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
            GridConstraints.SIZEPOLICY_FIXED,
            null,
            null,
            null,
            0,
            false));
    ButtonGroup buttonGroup;
    buttonGroup = new ButtonGroup();
    buttonGroup.add(launchDefaultButton);
    buttonGroup.add(launchCustomButton);
    buttonGroup.add(launchNothingButton);
  }

  /** Initially generated by IntelliJ from a .form file. */
  private void loadButtonText(AbstractButton component, String text) {
    StringBuffer result = new StringBuffer();
    boolean haveMnemonic = false;
    char mnemonic = '\0';
    int mnemonicIndex = -1;
    for (int i = 0; i < text.length(); i++) {
      if (text.charAt(i) == '&') {
        i++;
        if (i == text.length()) {
          break;
        }
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
