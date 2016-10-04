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
package com.google.idea.blaze.base.run.confighandler;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.BlazeCommandRunConfigurationType;
import com.google.idea.blaze.base.run.confighandler.BlazeCommandGenericRunConfigurationHandler.BlazeCommandGenericRunConfigurationHandlerEditor;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.util.InvalidDataException;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link BlazeCommandGenericRunConfigurationHandler}. */
@RunWith(JUnit4.class)
public class BlazeCommandGenericRunConfigurationHandlerTest extends BlazeTestCase {
  private static final BlazeImportSettings DUMMY_IMPORT_SETTINGS =
      new BlazeImportSettings("", "", "", "", "", Blaze.BuildSystem.Blaze);
  private static final BlazeCommandName COMMAND = BlazeCommandName.fromString("command");

  private final BlazeCommandRunConfigurationType type = new BlazeCommandRunConfigurationType();
  private BlazeCommandRunConfiguration configuration;
  private BlazeCommandGenericRunConfigurationHandler handler;

  @Override
  protected void initTest(
      @NotNull Container applicationServices, @NotNull Container projectServices) {
    super.initTest(applicationServices, projectServices);

    applicationServices.register(UISettings.class, new UISettings());
    projectServices.register(
        BlazeImportSettingsManager.class, new BlazeImportSettingsManager(project));
    BlazeImportSettingsManager.getInstance(getProject()).setImportSettings(DUMMY_IMPORT_SETTINGS);

    configuration = type.getFactory().createTemplateConfiguration(project);
    handler = new BlazeCommandGenericRunConfigurationHandler(configuration);
  }

  @Test
  public void readAndWriteShouldMatch() throws InvalidDataException {
    handler.setCommand(COMMAND);
    handler.setBlazeFlags(ImmutableList.of("--flag1", "--flag2"));
    handler.setExeFlags(ImmutableList.of("--exeFlag1"));
    handler.setBlazeBinary("/usr/bin/blaze");

    Element element = new Element("test");
    handler.writeExternal(element);
    BlazeCommandRunConfiguration readConfiguration =
        type.getFactory().createTemplateConfiguration(project);
    BlazeCommandGenericRunConfigurationHandler readHandler =
        new BlazeCommandGenericRunConfigurationHandler(readConfiguration);
    readHandler.readExternal(element);

    assertThat(readHandler.getCommand()).isEqualTo(COMMAND);
    assertThat(readHandler.getAllBlazeFlags()).containsExactly("--flag1", "--flag2").inOrder();
    assertThat(readHandler.getAllExeFlags()).containsExactly("--exeFlag1");
    assertThat(readHandler.getBlazeBinary()).isEqualTo("/usr/bin/blaze");
  }

  @Test
  public void readAndWriteShouldHandleNulls() throws InvalidDataException {
    Element element = new Element("test");
    handler.writeExternal(element);
    BlazeCommandRunConfiguration readConfiguration =
        type.getFactory().createTemplateConfiguration(project);
    BlazeCommandGenericRunConfigurationHandler readHandler =
        new BlazeCommandGenericRunConfigurationHandler(readConfiguration);
    readHandler.readExternal(element);

    assertThat(readHandler.getCommand()).isEqualTo(handler.getCommand());
    assertThat(readHandler.getAllBlazeFlags()).isEqualTo(handler.getAllBlazeFlags());
    assertThat(readHandler.getAllExeFlags()).isEqualTo(handler.getAllExeFlags());
    assertThat(readHandler.getBlazeBinary()).isEqualTo(handler.getBlazeBinary());
  }

  @Test
  public void readShouldOmitEmptyFlags() throws InvalidDataException {
    handler.setBlazeFlags(Lists.newArrayList("hi ", "", "I'm", " ", "\t", "Josh\r\n", "\n"));
    handler.setExeFlags(Lists.newArrayList("hi ", "", "I'm", " ", "\t", "Josh\r\n", "\n"));

    Element element = new Element("test");
    handler.writeExternal(element);
    BlazeCommandRunConfiguration readConfiguration =
        type.getFactory().createTemplateConfiguration(project);
    BlazeCommandGenericRunConfigurationHandler readHandler =
        new BlazeCommandGenericRunConfigurationHandler(readConfiguration);
    readHandler.readExternal(element);

    assertThat(readHandler.getAllBlazeFlags()).containsExactly("hi", "I'm", "Josh").inOrder();
    assertThat(readHandler.getAllExeFlags()).containsExactly("hi", "I'm", "Josh").inOrder();
  }

  @Test
  public void editorApplyToAndResetFromShouldMatch() throws ConfigurationException {
    BlazeCommandGenericRunConfigurationHandlerEditor editor =
        new BlazeCommandGenericRunConfigurationHandlerEditor(handler);

    handler.setCommand(COMMAND);
    handler.setBlazeFlags(ImmutableList.of("--flag1", "--flag2"));
    handler.setExeFlags(ImmutableList.of("--exeFlag1", "--exeFlag2"));
    handler.setBlazeBinary("/usr/bin/blaze");

    editor.resetEditorFrom(handler);
    BlazeCommandRunConfiguration readConfiguration =
        type.getFactory().createTemplateConfiguration(project);
    BlazeCommandGenericRunConfigurationHandler readHandler =
        new BlazeCommandGenericRunConfigurationHandler(readConfiguration);
    editor.applyEditorTo(readHandler);

    assertThat(readHandler.getCommand()).isEqualTo(handler.getCommand());
    assertThat(readHandler.getAllBlazeFlags()).isEqualTo(handler.getAllBlazeFlags());
    assertThat(readHandler.getAllExeFlags()).isEqualTo(handler.getAllExeFlags());
    assertThat(readHandler.getBlazeBinary()).isEqualTo(handler.getBlazeBinary());
  }

  @Test
  public void editorApplyToAndResetFromShouldHandleNulls() throws ConfigurationException {
    BlazeCommandGenericRunConfigurationHandlerEditor editor =
        new BlazeCommandGenericRunConfigurationHandlerEditor(handler);

    editor.resetEditorFrom(handler);
    BlazeCommandRunConfiguration readConfiguration =
        type.getFactory().createTemplateConfiguration(project);
    BlazeCommandGenericRunConfigurationHandler readHandler =
        new BlazeCommandGenericRunConfigurationHandler(readConfiguration);
    editor.applyEditorTo(readHandler);

    assertThat(readHandler.getCommand()).isEqualTo(handler.getCommand());
    assertThat(readHandler.getAllBlazeFlags()).isEqualTo(handler.getAllBlazeFlags());
    assertThat(readHandler.getAllExeFlags()).isEqualTo(handler.getAllExeFlags());
    assertThat(readHandler.getBlazeBinary()).isEqualTo(handler.getBlazeBinary());
  }
}
