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
package com.google.idea.blaze.android.run.runner;

import com.android.tools.idea.run.ValidationError;
import com.android.tools.idea.run.editor.AndroidDebugger;
import com.android.tools.idea.run.editor.AndroidDebuggerState;
import com.android.tools.idea.run.editor.AndroidJavaDebugger;
import com.android.tools.ndk.run.editor.NativeAndroidDebuggerState;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.idea.blaze.android.cppapi.BlazeNativeDebuggerIdProvider;
import com.google.idea.blaze.android.run.BlazeAndroidRunConfigurationCommonState;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.jdom.Element;
import org.jetbrains.android.facet.AndroidFacet;

/** Manages android debugger state for the run configurations. */
public class BlazeAndroidRunConfigurationDebuggerManager implements JDOMExternalizable {
  private final Map<String, AndroidDebuggerState> androidDebuggerStates = Maps.newHashMap();
  private final BlazeAndroidRunConfigurationCommonState commonState;

  public BlazeAndroidRunConfigurationDebuggerManager(
      BlazeAndroidRunConfigurationCommonState commonState) {
    this.commonState = commonState;
    for (AndroidDebugger androidDebugger : getAndroidDebuggers()) {
      this.androidDebuggerStates.put(androidDebugger.getId(), androidDebugger.createState());
    }
  }

  public List<ValidationError> validate(AndroidFacet facet) {
    // All of the AndroidDebuggerState classes implement a validate that
    // either does nothing or is specific to gradle so there is no point
    // in calling validate on our AndroidDebuggerState.
    return ImmutableList.of();
  }

  @Nullable
  AndroidDebugger getAndroidDebugger() {
    String debuggerID = getDebuggerID();
    for (AndroidDebugger androidDebugger : getAndroidDebuggers()) {
      if (androidDebugger.getId().equals(debuggerID)) {
        return androidDebugger;
      }
    }
    return null;
  }

  @Nullable
  final <T extends AndroidDebuggerState> T getAndroidDebuggerState(Project project) {
    T androidDebuggerState = getAndroidDebuggerState(getDebuggerID());
    // Set our working directory to our workspace root for native debugging.
    if (androidDebuggerState instanceof NativeAndroidDebuggerState) {
      NativeAndroidDebuggerState nativeState = (NativeAndroidDebuggerState) androidDebuggerState;
      String workingDirPath = WorkspaceRoot.fromProject(project).directory().getPath();
      nativeState.setWorkingDir(workingDirPath);
    }
    return androidDebuggerState;
  }

  private static List<AndroidDebugger> getAndroidDebuggers() {
    // This includes the native debugger(s).
    return Arrays.asList(AndroidDebugger.EP_NAME.getExtensions());
  }

  private String getDebuggerID() {
    BlazeNativeDebuggerIdProvider blazeNativeDebuggerIdProvider =
        BlazeNativeDebuggerIdProvider.getInstance();
    return (blazeNativeDebuggerIdProvider != null && commonState.isNativeDebuggingEnabled())
        ? blazeNativeDebuggerIdProvider.getDebuggerId()
        : AndroidJavaDebugger.ID;
  }

  @Nullable
  private final <T extends AndroidDebuggerState> T getAndroidDebuggerState(
      String androidDebuggerId) {
    AndroidDebuggerState state = androidDebuggerStates.get(androidDebuggerId);
    return (state != null) ? (T) state : null;
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    for (Map.Entry<String, AndroidDebuggerState> entry : androidDebuggerStates.entrySet()) {
      Element optionElement = element.getChild(entry.getKey());
      if (optionElement != null) {
        entry.getValue().readExternal(optionElement);
      }
    }
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    for (Map.Entry<String, AndroidDebuggerState> entry : androidDebuggerStates.entrySet()) {
      Element optionElement = new Element(entry.getKey());
      element.addContent(optionElement);
      entry.getValue().writeExternal(optionElement);
    }
  }
}
