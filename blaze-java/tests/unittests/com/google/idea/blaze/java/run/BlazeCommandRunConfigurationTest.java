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
package com.google.idea.blaze.java.run;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.experiments.ExperimentService;
import com.google.idea.blaze.base.experiments.MockExperimentService;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.settings.Blaze.BuildSystem;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.java.run.BlazeCommandRunConfiguration.BlazeCommandRunConfigurationSettingsEditor;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static com.google.common.truth.Truth.assertThat;

/**
 * Tests for {@link BlazeCommandRunConfiguration}.
 */
@RunWith(JUnit4.class)
public class BlazeCommandRunConfigurationTest extends BlazeTestCase {
  private static Label label;
  private static final BlazeCommandName COMMAND = BlazeCommandName.fromString("command");
  private static final BlazeImportSettings DUMMY_IMPORT_SETTINGS = new BlazeImportSettings("", "", "", "", "", BuildSystem.Blaze);

  BlazeCommandRunConfigurationType type = new BlazeCommandRunConfigurationType();
  BlazeCommandRunConfiguration configuration;


  @Override
  protected void initTest(
    @NotNull Container applicationServices,
    @NotNull Container projectServices) {
    super.initTest(applicationServices, projectServices);

    applicationServices.register(ExperimentService.class, new MockExperimentService());
    projectServices.register(BlazeImportSettingsManager.class, new BlazeImportSettingsManager(project));
    BlazeImportSettingsManager.getInstance(getProject()).setImportSettings(DUMMY_IMPORT_SETTINGS);

    configuration = type.getFactory().createTemplateConfiguration(project);
    label = new Label("//package:rule");
  }

  @Test
  public void readAndWriteShouldMatch() throws InvalidDataException, WriteExternalException {
    configuration.setTarget(label);
    configuration.setCommand(COMMAND);
    configuration.setBlazeFlags(ImmutableList.of("--flag1", "--flag2"));
    configuration.setExeFlags(ImmutableList.of("--exeFlag1"));
    Element element = new Element("test");
    configuration.writeExternal(element);
    BlazeCommandRunConfiguration readConfiguration =
      type.getFactory().createTemplateConfiguration(project);
    readConfiguration.readExternal(element);
    assertThat(readConfiguration.getTarget()).isEqualTo(label);
    assertThat(readConfiguration.getCommand()).isEqualTo(COMMAND);
    assertThat(readConfiguration.getAllBlazeFlags()).isEqualTo(ImmutableList.of("--flag1", "--flag2"));
    assertThat(readConfiguration.getAllExeFlags()).isEqualTo(ImmutableList.of("--exeFlag1"));
  }

  @Test
  public void readAndWriteShouldHandleNulls() throws InvalidDataException, WriteExternalException {
    Element element = new Element("test");
    configuration.writeExternal(element);
    BlazeCommandRunConfiguration readConfiguration =
      type.getFactory().createTemplateConfiguration(project);
    readConfiguration.readExternal(element);
    assertThat(readConfiguration.getTarget()).isEqualTo(configuration.getTarget());
    assertThat(readConfiguration.getCommand()).isEqualTo(configuration.getCommand());
    assertThat(readConfiguration.getAllBlazeFlags()).isEqualTo(configuration.getAllBlazeFlags());
    assertThat(readConfiguration.getAllExeFlags()).isEqualTo(configuration.getAllExeFlags());
  }

  @Test
  public void readShouldOmitEmptyFlags() throws InvalidDataException, WriteExternalException {
    configuration.setBlazeFlags(Lists.newArrayList("hi ", "", "I'm", " ", "\t", "Josh\r\n", "\n"));
    configuration.setExeFlags(Lists.newArrayList("hi ", "", "I'm", " ", "\t", "Josh\r\n", "\n"));
    Element element = new Element("test");
    configuration.writeExternal(element);
    BlazeCommandRunConfiguration readConfiguration =
      type.getFactory().createTemplateConfiguration(project);
    readConfiguration.readExternal(element);
    assertThat(readConfiguration.getAllBlazeFlags()).isEqualTo(ImmutableList.of("hi", "I'm", "Josh"));
    assertThat(readConfiguration.getAllExeFlags()).isEqualTo(ImmutableList.of("hi", "I'm", "Josh"));
  }

  @Test
  public void editorApplyToAndResetFromShouldHandleNulls() throws ConfigurationException {
    BlazeCommandRunConfigurationSettingsEditor editor =
      new BlazeCommandRunConfigurationSettingsEditor("Blaze");
    editor.resetFrom(configuration);
    BlazeCommandRunConfiguration readConfiguration =
      type.getFactory().createTemplateConfiguration(project);
    editor.applyEditorTo(readConfiguration);
    assertThat(readConfiguration.getTarget()).isEqualTo(configuration.getTarget());
    assertThat(readConfiguration.getCommand()).isEqualTo(configuration.getCommand());
    assertThat(readConfiguration.getAllBlazeFlags()).isEqualTo(configuration.getAllBlazeFlags());
    assertThat(readConfiguration.getAllExeFlags()).isEqualTo(configuration.getAllExeFlags());

    Disposer.dispose(editor);
  }

  @Test
  public void editorApplyToAndResetFromShouldMatch() throws ConfigurationException {
    BlazeCommandRunConfigurationSettingsEditor editor = new BlazeCommandRunConfigurationSettingsEditor("Blaze");
    configuration.setTarget(label);
    configuration.setCommand(COMMAND);
    configuration.setBlazeFlags(ImmutableList.of("--flag1", "--flag2"));
    configuration.setExeFlags(ImmutableList.of("--exeFlag1", "--exeFlag2"));
    editor.resetFrom(configuration);

    BlazeCommandRunConfiguration readConfiguration = type.getFactory().createTemplateConfiguration(project);
    editor.applyEditorTo(readConfiguration);
    assertThat(readConfiguration.getTarget()).isEqualTo(configuration.getTarget());
    assertThat(readConfiguration.getCommand()).isEqualTo(configuration.getCommand());
    assertThat(readConfiguration.getAllBlazeFlags()).isEqualTo(configuration.getAllBlazeFlags());
    assertThat(readConfiguration.getAllExeFlags()).isEqualTo(configuration.getAllExeFlags());
    
    Disposer.dispose(editor);
  }
}
