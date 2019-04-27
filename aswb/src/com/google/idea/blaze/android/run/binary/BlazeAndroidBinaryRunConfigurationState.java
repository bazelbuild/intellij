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

import com.android.tools.idea.run.util.LaunchUtils;
import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import com.google.idea.blaze.android.run.BlazeAndroidRunConfigurationCommonState;
import com.google.idea.blaze.android.run.binary.AndroidBinaryLaunchMethodsUtils.AndroidBinaryLaunchMethod;
import com.google.idea.blaze.base.run.state.RunConfigurationState;
import com.google.idea.blaze.base.run.state.RunConfigurationStateEditor;
import com.google.idea.blaze.base.ui.UiUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import java.util.Map;
import javax.swing.JComponent;
import org.jdom.Element;

/** State specific to the android binary run configuration. */
public final class BlazeAndroidBinaryRunConfigurationState
    extends BlazeAndroidRunConfigurationCommonState {
  public static final String LAUNCH_DEFAULT_ACTIVITY = "default_activity";
  public static final String LAUNCH_SPECIFIC_ACTIVITY = "specific_activity";
  public static final String DO_NOTHING = "do_nothing";
  public static final String LAUNCH_DEEP_LINK = "launch_deep_link";

  private final BinarySettings binarySettings = new BinarySettings();

  BlazeAndroidBinaryRunConfigurationState(String buildSystemName) {
    super(buildSystemName, false);
  }

  public AndroidBinaryLaunchMethod getLaunchMethod() {
    return binarySettings.launchMethod;
  }

  void setLaunchMethod(AndroidBinaryLaunchMethod launchMethod) {
    binarySettings.launchMethod = launchMethod;
  }

  // binarySettings method is deprecated, as unused by mobile-install v2.
  // TODO(timpeut): cleanup once ASwB has released and no rollback is required
  public boolean useSplitApksIfPossible() {
    return binarySettings.useSplitApksIfPossible;
  }

  // binarySettings method is deprecated, as unused by mobile-install v2.
  // TODO(timpeut): cleanup once ASwB has released and no rollback is required
  void setUseSplitApksIfPossible(boolean useSplitApksIfPossible) {
    binarySettings.useSplitApksIfPossible = useSplitApksIfPossible;
  }

  public boolean useWorkProfileIfPresent() {
    return binarySettings.useWorkProfileIfPresent;
  }

  void setUseWorkProfileIfPresent(boolean useWorkProfileIfPresent) {
    binarySettings.useWorkProfileIfPresent = useWorkProfileIfPresent;
  }

  Integer getUserId() {
    return binarySettings.userId;
  }

  void setUserId(Integer userId) {
    binarySettings.userId = userId;
  }

  public boolean showLogcatAutomatically() {
    return binarySettings.showLogcatAutomatically;
  }

  public void setShowLogcatAutomatically(boolean showLogcatAutomatically) {
    binarySettings.showLogcatAutomatically = showLogcatAutomatically;
  }

  public String getDeepLink() {
    return binarySettings.deepLink;
  }

  public void setDeepLink(String deepLink) {
    binarySettings.deepLink = deepLink;
  }

  public String getActivityClass() {
    return binarySettings.activityClass;
  }

  public void setActivityClass(String activityClass) {
    binarySettings.activityClass = activityClass;
  }

  public String getMode() {
    return binarySettings.mode;
  }

  public void setMode(String mode) {
    binarySettings.mode = mode;
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    super.readExternal(element);
    binarySettings.readExternal(element);
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    super.writeExternal(element);
    binarySettings.writeExternal(element);
  }

  @Override
  public RunConfigurationStateEditor getEditor(Project project) {
    final RunConfigurationStateEditor binarySettingsEditor = binarySettings.getEditor(project);
    final RunConfigurationStateEditor commonStateEditor = super.getEditor(project);

    return new RunConfigurationStateEditor() {
      @Override
      public void resetEditorFrom(RunConfigurationState state) {
        commonStateEditor.resetEditorFrom(state);
        binarySettingsEditor.resetEditorFrom(state);
      }

      @Override
      public void applyEditorTo(RunConfigurationState state) {
        commonStateEditor.applyEditorTo(state);
        binarySettingsEditor.applyEditorTo(state);
      }

      @Override
      public JComponent createComponent() {
        return UiUtil.createBox(
            commonStateEditor.createComponent(), binarySettingsEditor.createComponent());
      }

      @Override
      public void setComponentEnabled(boolean enabled) {
        commonStateEditor.setComponentEnabled(enabled);
        binarySettingsEditor.setComponentEnabled(enabled);
      }
    };
  }

  private static class BinarySettings implements RunConfigurationState {
    static final String LAUNCH_METHOD_ATTR = "launch-method";
    // Remove once v2 becomes default.
    static final String USE_SPLIT_APKS_IF_POSSIBLE = "use-split-apks-if-possible";

    static final String WORK_PROFILE_ATTR = "use-work-profile-if-present";
    static final String USER_ID_ATTR = "user-id";

    AndroidBinaryLaunchMethod launchMethod = AndroidBinaryLaunchMethod.MOBILE_INSTALL;
    boolean useSplitApksIfPossible = false;
    boolean useWorkProfileIfPresent = false;
    Integer userId;

    static final String SHOW_LOGCAT_AUTOMATICALLY = "show-logcat-automatically";
    boolean showLogcatAutomatically = false;

    static final String DEEP_LINK = "DEEP_LINK";
    static final String ACTIVITY_CLASS = "ACTIVITY_CLASS";
    static final String MODE = "MODE";
    static final String ACTIVITY_EXTRA_FLAGS = "ACTIVITY_EXTRA_FLAGS";
    String deepLink = "";
    String activityClass = "";
    String mode = LAUNCH_DEFAULT_ACTIVITY;

    @Override
    public void readExternal(Element element) throws InvalidDataException {
      deepLink = Strings.nullToEmpty(element.getAttributeValue(DEEP_LINK));
      activityClass = Strings.nullToEmpty(element.getAttributeValue(ACTIVITY_CLASS));
      String modeValue = element.getAttributeValue(MODE);
      mode = Strings.isNullOrEmpty(modeValue) ? LAUNCH_DEFAULT_ACTIVITY : modeValue;
      String launchMethodAttribute = element.getAttributeValue(LAUNCH_METHOD_ATTR);
      if (launchMethodAttribute != null) {
        launchMethod = AndroidBinaryLaunchMethod.valueOf(launchMethodAttribute);
      } else {
        launchMethod = AndroidBinaryLaunchMethod.MOBILE_INSTALL;
      }
      useSplitApksIfPossible =
          Boolean.parseBoolean(element.getAttributeValue(USE_SPLIT_APKS_IF_POSSIBLE));
      useWorkProfileIfPresent = Boolean.parseBoolean(element.getAttributeValue(WORK_PROFILE_ATTR));

      String userIdString = element.getAttributeValue(USER_ID_ATTR);
      if (userIdString != null) {
        userId = Integer.parseInt(userIdString);
      }

      showLogcatAutomatically =
          Boolean.parseBoolean(element.getAttributeValue(SHOW_LOGCAT_AUTOMATICALLY));

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
    public void writeExternal(Element element) throws WriteExternalException {
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

    /**
     * Imports legacy values in the old reflective JDOM externalizer manner. Can be removed ~2.0+.
     */
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
      return new BlazeAndroidBinaryRunConfigurationStateEditor(project);
    }
  }
}
