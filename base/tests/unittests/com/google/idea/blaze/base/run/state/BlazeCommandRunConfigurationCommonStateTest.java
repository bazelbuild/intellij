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
package com.google.idea.blaze.base.run.state;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.intellij.ide.ui.UISettings;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link BlazeCommandRunConfigurationCommonState}. */
@RunWith(JUnit4.class)
public class BlazeCommandRunConfigurationCommonStateTest extends BlazeTestCase {
  private static final BlazeImportSettings DUMMY_IMPORT_SETTINGS =
      new BlazeImportSettings("", "", "", "", "", Blaze.BuildSystem.Blaze);
  private static final BlazeCommandName COMMAND = BlazeCommandName.fromString("command");

  private BlazeCommandRunConfigurationCommonState state;

  @Override
  protected void initTest(
      @NotNull Container applicationServices, @NotNull Container projectServices) {
    super.initTest(applicationServices, projectServices);

    applicationServices.register(UISettings.class, new UISettings());
    projectServices.register(
        BlazeImportSettingsManager.class, new BlazeImportSettingsManager(project));
    BlazeImportSettingsManager.getInstance(getProject()).setImportSettings(DUMMY_IMPORT_SETTINGS);

    state = new BlazeCommandRunConfigurationCommonState(Blaze.buildSystemName(project));
  }

  @Test
  public void readAndWriteShouldMatch() throws Exception {
    state.setCommand(COMMAND);
    state.setBlazeFlags(ImmutableList.of("--flag1", "--flag2"));
    state.setExeFlags(ImmutableList.of("--exeFlag1"));
    state.setBlazeBinary("/usr/bin/blaze");

    Element element = new Element("test");
    state.writeExternal(element);
    BlazeCommandRunConfigurationCommonState readState =
        new BlazeCommandRunConfigurationCommonState(Blaze.buildSystemName(project));
    readState.readExternal(element);

    assertThat(readState.getCommand()).isEqualTo(COMMAND);
    assertThat(readState.getBlazeFlags()).containsExactly("--flag1", "--flag2").inOrder();
    assertThat(readState.getExeFlags()).containsExactly("--exeFlag1");
    assertThat(readState.getBlazeBinary()).isEqualTo("/usr/bin/blaze");
  }

  @Test
  public void readAndWriteShouldHandleNulls() throws Exception {
    Element element = new Element("test");
    state.writeExternal(element);
    BlazeCommandRunConfigurationCommonState readState =
        new BlazeCommandRunConfigurationCommonState(Blaze.buildSystemName(project));
    readState.readExternal(element);

    assertThat(readState.getCommand()).isEqualTo(state.getCommand());
    assertThat(readState.getBlazeFlags()).isEqualTo(state.getBlazeFlags());
    assertThat(readState.getExeFlags()).isEqualTo(state.getExeFlags());
    assertThat(readState.getBlazeBinary()).isEqualTo(state.getBlazeBinary());
  }

  @Test
  public void readShouldOmitEmptyFlags() throws Exception {
    state.setBlazeFlags(Lists.newArrayList("hi ", "", "I'm", " ", "\t", "Josh\r\n", "\n"));
    state.setExeFlags(Lists.newArrayList("hi ", "", "I'm", " ", "\t", "Josh\r\n", "\n"));

    Element element = new Element("test");
    state.writeExternal(element);
    BlazeCommandRunConfigurationCommonState readState =
        new BlazeCommandRunConfigurationCommonState(Blaze.buildSystemName(project));
    readState.readExternal(element);

    assertThat(readState.getBlazeFlags()).containsExactly("hi", "I'm", "Josh").inOrder();
    assertThat(readState.getExeFlags()).containsExactly("hi", "I'm", "Josh").inOrder();
  }

  @Test
  public void editorApplyToAndResetFromShouldMatch() throws Exception {
    RunConfigurationStateEditor editor = state.getEditor(project);

    state.setCommand(COMMAND);
    state.setBlazeFlags(ImmutableList.of("--flag1", "--flag2"));
    state.setExeFlags(ImmutableList.of("--exeFlag1", "--exeFlag2"));
    state.setBlazeBinary("/usr/bin/blaze");

    editor.resetEditorFrom(state);
    BlazeCommandRunConfigurationCommonState readState =
        new BlazeCommandRunConfigurationCommonState(Blaze.buildSystemName(project));
    editor.applyEditorTo(readState);

    assertThat(readState.getCommand()).isEqualTo(state.getCommand());
    assertThat(readState.getBlazeFlags()).isEqualTo(state.getBlazeFlags());
    assertThat(readState.getExeFlags()).isEqualTo(state.getExeFlags());
    assertThat(readState.getBlazeBinary()).isEqualTo(state.getBlazeBinary());
  }

  @Test
  public void editorApplyToAndResetFromShouldHandleNulls() throws Exception {
    RunConfigurationStateEditor editor = state.getEditor(project);

    editor.resetEditorFrom(state);
    BlazeCommandRunConfigurationCommonState readState =
        new BlazeCommandRunConfigurationCommonState(Blaze.buildSystemName(project));
    editor.applyEditorTo(readState);

    assertThat(readState.getCommand()).isEqualTo(state.getCommand());
    assertThat(readState.getBlazeFlags()).isEqualTo(state.getBlazeFlags());
    assertThat(readState.getExeFlags()).isEqualTo(state.getExeFlags());
    assertThat(readState.getBlazeBinary()).isEqualTo(state.getBlazeBinary());
  }
}
