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
package com.google.idea.blaze.android.run;

import static com.google.idea.blaze.android.cppapi.NdkSupport.NDK_SUPPORT;

import com.google.common.collect.ImmutableList;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import java.util.List;
import org.jdom.Element;

/**
 * A shared state class for run configurations targeting Blaze Android rules. We implement the
 * deprecated JDomExternalizable to fit with the other run configs.
 */
public class BlazeAndroidRunConfigurationCommonState implements BlazeAndroidRunConfigurationState {
  private static final String USER_FLAG_TAG = "blaze-user-flag";
  private static final String NATIVE_DEBUG_ATTR = "blaze-native-debug";

  private List<String> userFlags;
  private boolean nativeDebuggingEnabled = false;

  /** Creates a configuration state initialized with the given flags. */
  public BlazeAndroidRunConfigurationCommonState(List<String> userFlags) {
    this.userFlags = userFlags;
  }

  public List<String> getUserFlags() {
    return userFlags;
  }

  public void setUserFlags(List<String> userFlags) {
    this.userFlags = userFlags;
  }

  public boolean isNativeDebuggingEnabled() {
    return nativeDebuggingEnabled && NDK_SUPPORT.getValue();
  }

  public void setNativeDebuggingEnabled(boolean nativeDebuggingEnabled) {
    this.nativeDebuggingEnabled = nativeDebuggingEnabled;
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    ImmutableList.Builder<String> flagsBuilder = ImmutableList.builder();
    for (Element e : element.getChildren(USER_FLAG_TAG)) {
      String flag = e.getTextTrim();
      if (flag != null && !flag.isEmpty()) {
        flagsBuilder.add(flag);
      }
    }
    userFlags = flagsBuilder.build();
    setNativeDebuggingEnabled(Boolean.parseBoolean(element.getAttributeValue(NATIVE_DEBUG_ATTR)));
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    for (String flag : userFlags) {
      Element child = new Element(USER_FLAG_TAG);
      child.setText(flag);
      element.addContent(child);
    }
    element.setAttribute(NATIVE_DEBUG_ATTR, Boolean.toString(nativeDebuggingEnabled));
  }
}
