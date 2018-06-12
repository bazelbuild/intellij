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
package com.google.idea.blaze.base.run.exporter;

import static com.google.common.truth.Truth.assertThat;

import com.google.idea.blaze.base.BlazeIntegrationTestCase;
import com.google.idea.blaze.base.model.MockBlazeProjectDataBuilder;
import com.google.idea.blaze.base.model.MockBlazeProjectDataManager;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.BlazeCommandRunConfigurationType;
import com.google.idea.blaze.base.run.state.BlazeCommandRunConfigurationCommonState;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import javax.annotation.Nullable;
import org.jdom.Element;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Test that {@link RunConfigurationSerializer} serializes/deserializes run configurations
 * correctly.
 */
@RunWith(JUnit4.class)
public class RunConfigurationSerializerTest extends BlazeIntegrationTestCase {

  private RunManagerImpl runManager;
  private BlazeCommandRunConfiguration configuration;

  @Before
  public final void doSetup() {
    runManager = RunManagerImpl.getInstanceImpl(getProject());
    // Without BlazeProjectData, the configuration editor is always disabled.
    BlazeProjectDataManager mockProjectDataManager =
        new MockBlazeProjectDataManager(MockBlazeProjectDataBuilder.builder(workspaceRoot).build());
    registerProjectService(BlazeProjectDataManager.class, mockProjectDataManager);

    RunnerAndConfigurationSettings runnerAndConfigurationSettings =
        runManager.createConfiguration(
            "Blaze Configuration", BlazeCommandRunConfigurationType.getInstance().getFactory());
    runManager.addConfiguration(runnerAndConfigurationSettings, false);
    configuration =
        (BlazeCommandRunConfiguration) runnerAndConfigurationSettings.getConfiguration();
  }

  @After
  public final void doTeardown() {
    clearRunManager();
  }

  private void clearRunManager() {
    runManager.clearAll();
    // We don't need to do this at setup, because it is handled by RunManagerImpl's constructor.
    // However, clearAll() clears the configuration types, so we need to reinitialize them.
    runManager.initializeConfigurationTypes(
        ConfigurationType.CONFIGURATION_TYPE_EP.getExtensions());
  }

  @Test
  public void testRunConfigurationUnalteredBySerializationRoundTrip() throws InvalidDataException {
    configuration.setTarget(Label.create("//package:rule"));
    configuration.setKeepInSync(true);

    Element initialElement = runManager.getState();

    Element element = RunConfigurationSerializer.writeToXml(configuration);
    assertThat(RunConfigurationSerializer.findExisting(getProject(), element)).isNotNull();

    clearRunManager(); // remove configuration from project
    RunConfigurationSerializer.loadFromXmlElementIgnoreExisting(getProject(), element);

    Element newElement = runManager.getState();
    XMLOutputter xmlOutputter = new XMLOutputter(Format.getCompactFormat());
    assertThat(xmlOutputter.outputString(newElement))
        .isEqualTo(xmlOutputter.outputString(initialElement));
  }

  @Test
  public void testSetKeepInSyncWhenImporting() throws InvalidDataException {
    configuration.setTarget(Label.create("//package:rule"));
    configuration.setKeepInSync(false);

    Element element = RunConfigurationSerializer.writeToXml(configuration);
    assertThat(RunConfigurationSerializer.findExisting(getProject(), element)).isNotNull();

    clearRunManager(); // remove configuration from project
    RunConfigurationSerializer.loadFromXmlElementIgnoreExisting(getProject(), element);

    RunConfiguration config = runManager.getAllConfigurations()[0];
    assertThat(config).isInstanceOf(BlazeCommandRunConfiguration.class);
    assertThat(((BlazeCommandRunConfiguration) config).getKeepInSync()).isTrue();
  }

  @Test
  public void testKeepInSyncRespectedWhenImporting() throws InvalidDataException {
    Element element = RunConfigurationSerializer.writeToXml(configuration);

    configuration.setKeepInSync(false);
    assertThat(RunConfigurationSerializer.shouldLoadConfiguration(getProject(), element)).isFalse();

    configuration.setKeepInSync(true);
    assertThat(RunConfigurationSerializer.shouldLoadConfiguration(getProject(), element)).isTrue();
  }

  @Test
  public void testConvertAbsolutePathToWorkspacePathVariableWhenSerializing() {
    if (isAndroidStudio()) {
      // #api171: disable for android studio -- path variable substitution isn't working in 2017.1
      return;
    }
    WorkspacePath binaryPath = WorkspacePath.createIfValid("path/to/binary/blaze");
    String absoluteBinaryPath = workspaceRoot.fileForPath(binaryPath).getPath();
    setBlazeBinaryPath(configuration, absoluteBinaryPath);

    Element element = RunConfigurationSerializer.writeToXml(configuration);
    assertThat(getBlazeBinaryPath(getProject(), element))
        .isEqualTo(
            String.format(
                "$%s$/%s", RunConfigurationSerializer.WORKSPACE_ROOT_VARIABLE_NAME, binaryPath));

    clearRunManager(); // remove configuration from project
    RunConfigurationSerializer.loadFromXmlElementIgnoreExisting(getProject(), element);

    RunConfiguration config = runManager.getAllConfigurations()[0];
    assertThat(config).isInstanceOf(BlazeCommandRunConfiguration.class);
    assertThat(getBlazeBinaryPath((BlazeCommandRunConfiguration) config))
        .isEqualTo(absoluteBinaryPath);
  }

  private static void setBlazeBinaryPath(BlazeCommandRunConfiguration configuration, String path) {
    BlazeCommandRunConfigurationCommonState state =
        configuration.getHandlerStateIfType(BlazeCommandRunConfigurationCommonState.class);
    state.getBlazeBinaryState().setBlazeBinary(path);
  }

  @Nullable
  private static String getBlazeBinaryPath(Project project, Element element) {
    BlazeCommandRunConfiguration config =
        BlazeCommandRunConfigurationType.getInstance()
            .getFactory()
            .createTemplateConfiguration(project);
    config.readExternal(element);
    return getBlazeBinaryPath(config);
  }

  @Nullable
  private static String getBlazeBinaryPath(BlazeCommandRunConfiguration configuration) {
    BlazeCommandRunConfigurationCommonState state =
        configuration.getHandlerStateIfType(BlazeCommandRunConfigurationCommonState.class);
    return state != null ? state.getBlazeBinaryState().getBlazeBinary() : null;
  }

  private static boolean isAndroidStudio() {
    return PluginManager.isPluginInstalled(PluginId.getId("org.jetbrains.android"))
        && PluginManager.isPluginInstalled(PluginId.getId("com.android.tools.ndk"));
  }
}
