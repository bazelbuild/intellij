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

import com.google.common.collect.Lists;
import com.google.idea.blaze.android.run.BlazeAndroidRunConfigurationCommonStateEditor;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.ui.UiUtil;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * A simplified, Blaze-specific variant of
 * {@link org.jetbrains.android.run.AndroidRunConfigurationEditor}.
 */
class BlazeAndroidBinaryRunConfigurationEditor extends SettingsEditor<BlazeAndroidBinaryRunConfiguration> {

  private final BlazeAndroidBinaryRunConfigurationStateEditor kindSpecificEditor;
  private final BlazeAndroidRunConfigurationCommonStateEditor commonStateEditor;

  public BlazeAndroidBinaryRunConfigurationEditor(
    Project project,
    BlazeAndroidBinaryRunConfigurationStateEditor kindSpecificEditor) {
    this.kindSpecificEditor = kindSpecificEditor;
    this.commonStateEditor = new BlazeAndroidRunConfigurationCommonStateEditor(project, Kind.ANDROID_BINARY);
  }

  @Override
  protected void resetEditorFrom(BlazeAndroidBinaryRunConfiguration configuration) {
    commonStateEditor.resetEditorFrom(configuration.getCommonState());
    kindSpecificEditor.resetFrom(configuration);
  }

  @Override
  protected void applyEditorTo(@NotNull BlazeAndroidBinaryRunConfiguration configuration)
    throws ConfigurationException {
    commonStateEditor.applyEditorTo(configuration.getCommonState());
    kindSpecificEditor.applyTo(configuration);
  }

  @Override
  @NotNull
  protected JComponent createEditor() {
    List<Component> components = Lists.newArrayList();
    components.addAll(commonStateEditor.getComponents());
    components.add(kindSpecificEditor.getComponent());
    return UiUtil.createBox(components);
  }
}
