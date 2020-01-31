/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
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
import com.android.tools.idea.run.editor.ProfilerState;
import com.android.tools.idea.run.util.LaunchUtils;
import com.google.common.base.Strings;
import com.google.common.collect.Maps;
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
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JRadioButton;
import org.jdom.Element;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidUtils;

/** State for android binary specific configurations. */
public class AndroidBinaryConfigState implements RunConfigurationState {
  /** Element name used to group the {@link ProfilerState} settings */
  public static final String LAUNCH_DEFAULT_ACTIVITY = "default_activity";

  public static final String LAUNCH_SPECIFIC_ACTIVITY = "specific_activity";
  public static final String DO_NOTHING = "do_nothing";
  public static final String LAUNCH_DEEP_LINK = "launch_deep_link";

  private static final String LAUNCH_METHOD_ATTR = "launch-method";
  // Remove once v2 becomes default.
  private static final String USE_SPLIT_APKS_IF_POSSIBLE = "use-split-apks-if-possible";

  private static final String WORK_PROFILE_ATTR = "use-work-profile-if-present";
  private static final String USER_ID_ATTR = "user-id";

  private AndroidBinaryLaunchMethodsUtils.AndroidBinaryLaunchMethod launchMethod =
      AndroidBinaryLaunchMethodsUtils.AndroidBinaryLaunchMethod.MOBILE_INSTALL;
  private boolean useSplitApksIfPossible = false;
  private boolean useWorkProfileIfPresent = false;
  private Integer userId;

  private static final String SHOW_LOGCAT_AUTOMATICALLY = "show-logcat-automatically";
  private boolean showLogcatAutomatically = false;

  private static final String DEEP_LINK = "DEEP_LINK";
  private static final String ACTIVITY_CLASS = "ACTIVITY_CLASS";
  private static final String MODE = "MODE";
  private static final String ACTIVITY_EXTRA_FLAGS = "ACTIVITY_EXTRA_FLAGS";
  private String deepLink = "";
  private String activityClass = "";
  private String mode = LAUNCH_DEFAULT_ACTIVITY;

  public AndroidBinaryLaunchMethodsUtils.AndroidBinaryLaunchMethod getLaunchMethod() {
    return launchMethod;
  }

  public void setLaunchMethod(
      AndroidBinaryLaunchMethodsUtils.AndroidBinaryLaunchMethod launchMethod) {
    this.launchMethod = launchMethod;
  }

  // This method is deprecated, as unused by mobile-install v2.
  // TODO(b/120300546): Remove once mobile-install v1 is completely deprecated.
  public boolean useSplitApksIfPossible() {
    return useSplitApksIfPossible;
  }

  // This method is deprecated, as unused by mobile-install v2.
  // TODO(b/120300546): Remove once mobile-install v1 is completely deprecated.
  public void setUseSplitApksIfPossible(boolean useSplitApksIfPossible) {
    this.useSplitApksIfPossible = useSplitApksIfPossible;
  }

  public boolean useWorkProfileIfPresent() {
    return useWorkProfileIfPresent;
  }

  public void setUseWorkProfileIfPresent(boolean useWorkProfileIfPresent) {
    this.useWorkProfileIfPresent = useWorkProfileIfPresent;
  }

  public Integer getUserId() {
    return userId;
  }

  public void setUserId(Integer userId) {
    this.userId = userId;
  }

  public boolean showLogcatAutomatically() {
    return showLogcatAutomatically;
  }

  public void setShowLogcatAutomatically(boolean showLogcatAutomatically) {
    this.showLogcatAutomatically = showLogcatAutomatically;
  }

  public String getDeepLink() {
    return deepLink;
  }

  public void setDeepLink(String deepLink) {
    this.deepLink = deepLink;
  }

  public String getActivityClass() {
    return activityClass;
  }

  public void setActivityClass(String activityClass) {
    this.activityClass = activityClass;
  }

  public String getMode() {
    return mode;
  }

  public void setMode(String mode) {
    this.mode = mode;
  }

  @Override
  public void readExternal(Element element) {
    setDeepLink(Strings.nullToEmpty(element.getAttributeValue(DEEP_LINK)));
    setActivityClass(Strings.nullToEmpty(element.getAttributeValue(ACTIVITY_CLASS)));
    String modeValue = element.getAttributeValue(MODE);
    setMode(Strings.isNullOrEmpty(modeValue) ? LAUNCH_DEFAULT_ACTIVITY : modeValue);
    String launchMethodAttribute = element.getAttributeValue(LAUNCH_METHOD_ATTR);
    if (launchMethodAttribute != null) {
      launchMethod =
          AndroidBinaryLaunchMethodsUtils.AndroidBinaryLaunchMethod.valueOf(launchMethodAttribute);
    } else {
      launchMethod = AndroidBinaryLaunchMethodsUtils.AndroidBinaryLaunchMethod.MOBILE_INSTALL;
    }
    setUseSplitApksIfPossible(
        Boolean.parseBoolean(element.getAttributeValue(USE_SPLIT_APKS_IF_POSSIBLE)));
    setUseWorkProfileIfPresent(Boolean.parseBoolean(element.getAttributeValue(WORK_PROFILE_ATTR)));

    String userIdString = element.getAttributeValue(USER_ID_ATTR);
    if (userIdString != null) {
      setUserId(Integer.parseInt(userIdString));
    }

    setShowLogcatAutomatically(
        Boolean.parseBoolean(element.getAttributeValue(SHOW_LOGCAT_AUTOMATICALLY)));

    for (Map.Entry<String, String> entry : getLegacyValues(element).entrySet()) {
      String value = entry.getValue();
      switch (entry.getKey()) {
        case DEEP_LINK:
          deepLink = Strings.nullToEmpty(value);
          break;
        case ACTIVITY_CLASS:
          activityClass = Strings.nullToEmpty(value);
          break;
        case MODE:
          mode = Strings.isNullOrEmpty(value) ? LAUNCH_DEFAULT_ACTIVITY : value;
          break;
        case ACTIVITY_EXTRA_FLAGS:
          if (userId == null) {
            userId = LaunchUtils.getUserIdFromFlags(value);
          }
          break;
        default:
          break;
      }
    }
  }

  @Override
  public void writeExternal(Element element) {
    element.setAttribute(DEEP_LINK, deepLink);
    element.setAttribute(ACTIVITY_CLASS, activityClass);
    element.setAttribute(MODE, mode);
    element.setAttribute(LAUNCH_METHOD_ATTR, launchMethod.name());
    element.setAttribute(USE_SPLIT_APKS_IF_POSSIBLE, Boolean.toString(useSplitApksIfPossible));
    element.setAttribute(WORK_PROFILE_ATTR, Boolean.toString(useWorkProfileIfPresent));
    element.setAttribute(SHOW_LOGCAT_AUTOMATICALLY, Boolean.toString(showLogcatAutomatically));

    if (userId != null) {
      element.setAttribute(USER_ID_ATTR, Integer.toString(userId));
    } else {
      element.removeAttribute(USER_ID_ATTR);
    }
  }

  /** Imports legacy values in the old reflective JDOM externalizer manner. Can be removed ~2.0+. */
  private static Map<String, String> getLegacyValues(Element element) {
    Map<String, String> result = Maps.newHashMap();
    for (Element option : element.getChildren("option")) {
      String name = option.getAttributeValue("name");
      String value = option.getAttributeValue("value");
      result.put(name, value);
    }
    return result;
  }

  @Override
  public RunConfigurationStateEditor getEditor(Project project) {
    return new Editor(project);
  }

  /** Editor for {@link AndroidBinaryConfigState} */
  public class Editor implements RunConfigurationStateEditor {
    private Box panel;
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

    public Editor(Project project) {
      setupUI(project);
      setupUiLogic(project);
    }

    @Override
    public void resetEditorFrom(RunConfigurationState genericState) {
      AndroidBinaryConfigState state =
          ((BlazeAndroidBinaryRunConfigurationState) genericState).getAndroidBinaryConfigState();

      boolean launchSpecificActivity = state.getMode().equals(LAUNCH_SPECIFIC_ACTIVITY);
      if (state.getMode().equals(LAUNCH_DEFAULT_ACTIVITY)) {
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
          AndroidBinaryLaunchMethodsUtils.useMobileInstall(state.getLaunchMethod()));
      useWorkProfileIfPresentCheckBox.setSelected(state.useWorkProfileIfPresent());
      userIdField.setValue(state.getUserId());
      showLogcatAutomaticallyCheckBox.setSelected(state.showLogcatAutomatically());

      updateEnabledState();
    }

    @Override
    public void applyEditorTo(RunConfigurationState genericState) {
      AndroidBinaryConfigState state =
          ((BlazeAndroidBinaryRunConfigurationState) genericState).getAndroidBinaryConfigState();

      state.setUserId((Integer) userIdField.getValue());
      if (launchDefaultButton.isSelected()) {
        state.setMode(LAUNCH_DEFAULT_ACTIVITY);
      } else if (launchCustomButton.isSelected()) {
        state.setMode(LAUNCH_SPECIFIC_ACTIVITY);
        state.setActivityClass(activityField.getChildComponent().getText());
      } else {
        state.setMode(DO_NOTHING);
      }
      state.setLaunchMethod(
          AndroidBinaryLaunchMethodsUtils.getLaunchMethod(useMobileInstallCheckBox.isSelected()));
      state.setUseWorkProfileIfPresent(useWorkProfileIfPresentCheckBox.isSelected());
      state.setShowLogcatAutomatically(showLogcatAutomaticallyCheckBox.isSelected());
    }

    @Override
    public JComponent createComponent() {
      return panel;
    }

    @Override
    public void setComponentEnabled(boolean enabled) {
      componentEnabled = enabled;
      updateEnabledState();
    }

    private void updateEnabledState() {
      boolean useWorkProfile = useWorkProfileIfPresentCheckBox.isSelected();
      userIdLabel.setEnabled(componentEnabled && !useWorkProfile);
      userIdField.setEnabled(componentEnabled && !useWorkProfile);
      activityField.setEnabled(componentEnabled && launchCustomButton.isSelected());
      launchNothingButton.setEnabled(componentEnabled);
      launchDefaultButton.setEnabled(componentEnabled);
      launchCustomButton.setEnabled(componentEnabled);
      useMobileInstallCheckBox.setEnabled(componentEnabled);
      useWorkProfileIfPresentCheckBox.setEnabled(componentEnabled);
      showLogcatAutomaticallyCheckBox.setEnabled(componentEnabled);
    }

    private void setupUiLogic(Project project) {
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
    }

    /** Create UI components. */
    private void setupUI(Project project) {
      // Mobile install settings
      useMobileInstallCheckBox = new JCheckBox();
      useMobileInstallCheckBox.setText("Use mobile-install");
      useMobileInstallCheckBox.setSelected(true);

      // User settings
      useWorkProfileIfPresentCheckBox = new JCheckBox();
      useWorkProfileIfPresentCheckBox.setText(" Use work profile if present");
      userIdLabel = new JLabel();
      userIdLabel.setText("User ID:");
      userIdField = new IntegerTextField();
      Box userPanel =
          UiUtil.createBox(
              useWorkProfileIfPresentCheckBox,
              UiUtil.createHorizontalBox(1, userIdLabel, userIdField));
      userPanel.setBorder(
          BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "User"));

      // Log cat
      showLogcatAutomaticallyCheckBox = new JCheckBox(" Show logcat automatically");

      // Activity launch options
      launchNothingButton = new JRadioButton();
      launchNothingButton.setText("Do not launch Activity");

      launchDefaultButton = new JRadioButton();
      launchDefaultButton.setText("Launch default Activity");
      launchDefaultButton.setMnemonic('L');
      launchDefaultButton.setDisplayedMnemonicIndex(0);

      launchCustomButton = new JRadioButton();
      launchCustomButton.setText("Launch:");
      launchCustomButton.setMnemonic('A');
      launchCustomButton.setDisplayedMnemonicIndex(1);

      activityField = createActivityField(project);

      ButtonGroup buttonGroup;
      buttonGroup = new ButtonGroup();
      buttonGroup.add(launchDefaultButton);
      buttonGroup.add(launchCustomButton);
      buttonGroup.add(launchNothingButton);

      Box activityPanel =
          UiUtil.createBox(
              launchNothingButton,
              launchDefaultButton,
              UiUtil.createHorizontalBox(0, launchCustomButton, activityField));
      activityPanel.setBorder(
          BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Activity"));

      // Panel to hold all the above editable components.
      panel =
          UiUtil.createBox(
              useMobileInstallCheckBox, showLogcatAutomaticallyCheckBox, activityPanel, userPanel);
    }

    /** Creates a textfield with browse button that browses through user activities. */
    private ComponentWithBrowseButton<EditorTextField> createActivityField(Project project) {
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
                  Key.create("BlazeActivityClassTextField"), AndroidBinaryConfigState.this);
              return editor;
            }
          };

      return new ComponentWithBrowseButton<>(editorTextField, null);
    }
  }
}
