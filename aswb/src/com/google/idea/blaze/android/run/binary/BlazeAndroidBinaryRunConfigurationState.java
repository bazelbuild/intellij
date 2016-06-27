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

import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;

/**
 * State specific to the android binary run configuration.
 */
public final class BlazeAndroidBinaryRunConfigurationState implements JDOMExternalizable {
  public static final String LAUNCH_DEFAULT_ACTIVITY = "default_activity";
  public static final String LAUNCH_SPECIFIC_ACTIVITY = "specific_activity";
  public static final String DO_NOTHING = "do_nothing";
  public static final String LAUNCH_DEEP_LINK = "launch_deep_link";
  public String DEEP_LINK = "";
  public String ACTIVITY_CLASS = "";

  public String MODE = LAUNCH_DEFAULT_ACTIVITY;
  // Launch options
  public String ACTIVITY_EXTRA_FLAGS = "";

  private static final String MOBILE_INSTALL_ATTR = "blaze-mobile-install";
  private static final String USE_SPLIT_APKS_IF_POSSIBLE = "use-split-apks-if-possible";
  private static final String INSTANT_RUN_ATTR = "instant-run";
  private boolean mobileInstall = false;
  private boolean useSplitApksIfPossible = true;
  private boolean instantRun = false;

  boolean isMobileInstall() {
    return mobileInstall;
  }

  void setMobileInstall(boolean mobileInstall) {
    this.mobileInstall = mobileInstall;
  }

  public boolean isUseSplitApksIfPossible() {
    return useSplitApksIfPossible;
  }

  void setUseSplitApksIfPossible(boolean useSplitApksIfPossible) {
    this.useSplitApksIfPossible = useSplitApksIfPossible;
  }

  boolean isInstantRun() {
    return instantRun;
  }

  void setInstantRun(boolean instantRun) {
    this.instantRun = instantRun;
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
    setMobileInstall(Boolean.parseBoolean(element.getAttributeValue(MOBILE_INSTALL_ATTR)));
    setUseSplitApksIfPossible(Boolean.parseBoolean(element.getAttributeValue(USE_SPLIT_APKS_IF_POSSIBLE)));
    setInstantRun(Boolean.parseBoolean(element.getAttributeValue(INSTANT_RUN_ATTR)));
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
    element.setAttribute(MOBILE_INSTALL_ATTR, Boolean.toString(mobileInstall));
    element.setAttribute(USE_SPLIT_APKS_IF_POSSIBLE, Boolean.toString(useSplitApksIfPossible));
    element.setAttribute(INSTANT_RUN_ATTR, Boolean.toString(instantRun));
  }
}
