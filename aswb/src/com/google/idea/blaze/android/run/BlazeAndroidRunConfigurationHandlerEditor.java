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

import com.google.common.collect.Lists;
import com.google.idea.blaze.base.run.confighandler.BlazeCommandRunConfigurationHandler;
import com.google.idea.blaze.base.run.confighandler.BlazeCommandRunConfigurationHandlerEditor;
import com.google.idea.blaze.base.ui.UiUtil;
import com.intellij.openapi.project.Project;
import java.awt.Component;
import java.util.List;
import javax.swing.JComponent;
import org.jetbrains.annotations.NotNull;

/**
 * A simplified, Blaze-specific variant of {@link
 * org.jetbrains.android.run.AndroidRunConfigurationEditor}.
 */
public class BlazeAndroidRunConfigurationHandlerEditor
    implements BlazeCommandRunConfigurationHandlerEditor {
  private final BlazeAndroidRunConfigurationCommonStateEditor commonStateEditor;
  private final BlazeAndroidRunConfigurationStateEditor kindSpecificEditor;

  public BlazeAndroidRunConfigurationHandlerEditor(
      Project project, BlazeAndroidRunConfigurationStateEditor kindSpecificEditor) {
    this.commonStateEditor = new BlazeAndroidRunConfigurationCommonStateEditor(project);
    this.kindSpecificEditor = kindSpecificEditor;
  }

  @Override
  public void resetEditorFrom(BlazeCommandRunConfigurationHandler h) {
    BlazeAndroidRunConfigurationHandler handler = (BlazeAndroidRunConfigurationHandler) h;
    commonStateEditor.resetEditorFrom(handler.getCommonState());
    kindSpecificEditor.resetEditorFrom(handler.getConfigState());
  }

  @Override
  public void applyEditorTo(BlazeCommandRunConfigurationHandler h) {
    BlazeAndroidRunConfigurationHandler handler = (BlazeAndroidRunConfigurationHandler) h;
    commonStateEditor.applyEditorTo(handler.getCommonState());
    kindSpecificEditor.applyEditorTo(handler.getConfigState());
  }

  @Override
  @NotNull
  public JComponent createEditor() {
    List<Component> components = Lists.newArrayList();
    components.addAll(commonStateEditor.getComponents());
    components.add(kindSpecificEditor.getComponent());
    return UiUtil.createBox(components);
  }
}
