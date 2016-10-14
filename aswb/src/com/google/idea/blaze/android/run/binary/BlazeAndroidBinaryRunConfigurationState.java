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

import com.android.tools.idea.run.ValidationError;
import com.android.tools.idea.run.util.LaunchUtils;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.idea.blaze.android.run.BlazeAndroidRunConfigurationCommonState;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.run.state.RunConfigurationState;
import com.google.idea.blaze.base.run.state.RunConfigurationStateEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.jdom.Element;
import org.jetbrains.android.facet.AndroidFacet;

/** State specific to the android binary run configuration. */
public final class BlazeAndroidBinaryRunConfigurationState implements RunConfigurationState {
  public static final String LAUNCH_DEFAULT_ACTIVITY = "default_activity";
  public static final String LAUNCH_SPECIFIC_ACTIVITY = "specific_activity";
  public static final String DO_NOTHING = "do_nothing";
  public static final String LAUNCH_DEEP_LINK = "launch_deep_link";

  private static final String MOBILE_INSTALL_ATTR = "blaze-mobile-install";
  private static final String USE_SPLIT_APKS_IF_POSSIBLE = "use-split-apks-if-possible";
  private static final String INSTANT_RUN_ATTR = "instant-run";
  private static final String WORK_PROFILE_ATTR = "use-work-profile-if-present";
  private static final String USER_ID_ATTR = "user-id";
  private boolean mobileInstall = false;
  private boolean useSplitApksIfPossible = true;
  private boolean instantRun = false;
  private boolean useWorkProfileIfPresent = false;
  private Integer userId;

  private static final String DEEP_LINK = "DEEP_LINK";
  private static final String ACTIVITY_CLASS = "ACTIVITY_CLASS";
  private static final String MODE = "MODE";
  private static final String ACTIVITY_EXTRA_FLAGS = "ACTIVITY_EXTRA_FLAGS";
  private String deepLink = "";
  private String activityClass = "";
  private String mode = LAUNCH_DEFAULT_ACTIVITY;

  private final BlazeAndroidRunConfigurationCommonState commonState;

  BlazeAndroidBinaryRunConfigurationState(String buildSystemName) {
    commonState = new BlazeAndroidRunConfigurationCommonState(buildSystemName, false);
  }

  public BlazeAndroidRunConfigurationCommonState getCommonState() {
    return commonState;
  }

  boolean mobileInstall() {
    return mobileInstall;
  }

  void setMobileInstall(boolean mobileInstall) {
    this.mobileInstall = mobileInstall;
  }

  public boolean useSplitApksIfPossible() {
    return useSplitApksIfPossible;
  }

  void setUseSplitApksIfPossible(boolean useSplitApksIfPossible) {
    this.useSplitApksIfPossible = useSplitApksIfPossible;
  }

  boolean instantRun() {
    return instantRun;
  }

  void setInstantRun(boolean instantRun) {
    this.instantRun = instantRun;
  }

  public boolean useWorkProfileIfPresent() {
    return useWorkProfileIfPresent;
  }

  void setUseWorkProfileIfPresent(boolean useWorkProfileIfPresent) {
    this.useWorkProfileIfPresent = useWorkProfileIfPresent;
  }

  Integer getUserId() {
    return userId;
  }

  void setUserId(Integer userId) {
    this.userId = userId;
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

  public ImmutableList<String> getBuildFlags(Project project, ProjectViewSet projectViewSet) {
    return commonState.getBuildFlags(project, projectViewSet);
  }

  /**
   * We collect errors rather than throwing to avoid missing fatal errors by exiting early for a
   * warning.
   */
  public List<ValidationError> validate(@Nullable AndroidFacet facet) {
    return commonState.validate(facet);
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    commonState.readExternal(element);

    setDeepLink(Strings.nullToEmpty(element.getAttributeValue(DEEP_LINK)));
    setActivityClass(Strings.nullToEmpty(element.getAttributeValue(ACTIVITY_CLASS)));
    setMode(Strings.nullToEmpty(element.getAttributeValue(MODE)));
    setMobileInstall(Boolean.parseBoolean(element.getAttributeValue(MOBILE_INSTALL_ATTR)));
    setUseSplitApksIfPossible(
        Boolean.parseBoolean(element.getAttributeValue(USE_SPLIT_APKS_IF_POSSIBLE)));
    setInstantRun(Boolean.parseBoolean(element.getAttributeValue(INSTANT_RUN_ATTR)));
    setUseWorkProfileIfPresent(Boolean.parseBoolean(element.getAttributeValue(WORK_PROFILE_ATTR)));

    String userIdString = element.getAttributeValue(USER_ID_ATTR);
    if (userIdString != null) {
      setUserId(Integer.parseInt(userIdString));
    }

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
          mode = Strings.nullToEmpty(value);
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
    commonState.writeExternal(element);

    element.setAttribute(DEEP_LINK, deepLink);
    element.setAttribute(ACTIVITY_CLASS, activityClass);
    element.setAttribute(MODE, mode);
    element.setAttribute(MOBILE_INSTALL_ATTR, Boolean.toString(mobileInstall));
    element.setAttribute(USE_SPLIT_APKS_IF_POSSIBLE, Boolean.toString(useSplitApksIfPossible));
    element.setAttribute(INSTANT_RUN_ATTR, Boolean.toString(instantRun));
    element.setAttribute(WORK_PROFILE_ATTR, Boolean.toString(useWorkProfileIfPresent));

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
    return new BlazeAndroidBinaryRunConfigurationStateEditor(
        commonState.getEditor(project), project);
  }
}
