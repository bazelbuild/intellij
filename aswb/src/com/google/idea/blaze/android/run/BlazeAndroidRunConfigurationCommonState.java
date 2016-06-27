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

import com.android.tools.idea.run.ValidationError;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.ideinfo.RuleIdeInfo;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.run.rulefinder.RuleFinder;
import com.google.idea.blaze.base.settings.Blaze;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.google.idea.blaze.android.cppapi.NdkSupport.NDK_SUPPORT;

/**
 * A shared state class for run configurations targeting Blaze Android rules.
 * We implement the deprecated JDomExternalizable to fit with the other run configs.
 */
public class BlazeAndroidRunConfigurationCommonState implements JDOMExternalizable {
  private static final String TARGET_ATTR = "blaze-target";
  private static final String USER_FLAG_TAG = "blaze-user-flag";
  private static final String NATIVE_DEBUG_ATTR = "blaze-native-debug";

  @Nullable private Label target;
  private List<String> userFlags;
  private boolean nativeDebuggingEnabled = false;

  /**
   * Creates a configuration state initialized with the given rule and flags.
   */
  public BlazeAndroidRunConfigurationCommonState(@Nullable Label target, List<String> userFlags) {
    this.target = target;
    this.userFlags = userFlags;
  }

  @Nullable
  public Label getTarget() {
    return target;
  }

  public void setTarget(@Nullable Label target) {
    this.target = target;
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

  public void checkConfiguration(Project project, Kind kind, List<ValidationError> errors) {
    RuleIdeInfo rule = target != null ? RuleFinder.getInstance().ruleForTarget(project, target) : null;
    if (rule == null) {
      errors.add(ValidationError.fatal(
        String.format("No existing %s rule selected.", Blaze.buildSystemName(project))
      ));
    }
    else if (!rule.kindIsOneOf(kind)) {
      errors.add(ValidationError.fatal(
        String.format("Selected %s rule is not %s", Blaze.buildSystemName(project), kind.toString())
      ));
    }
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);

    target = null;
    String targetString = element.getAttributeValue(TARGET_ATTR);
    if (targetString != null) {
      try {
        target = new Label(targetString);
      }
      catch (IllegalArgumentException e) {
        throw new InvalidDataException("Bad configuration target", e);
      }
    }
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
    DefaultJDOMExternalizer.writeExternal(this, element);

    if (target != null) {
      element.setAttribute(TARGET_ATTR, target.toString());
    }
    for (String flag : userFlags) {
      Element child = new Element(USER_FLAG_TAG);
      child.setText(flag);
      element.addContent(child);
    }
    element.setAttribute(NATIVE_DEBUG_ATTR, Boolean.toString(nativeDebuggingEnabled));
  }
}
