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

package com.google.idea.blaze.base.run;

import static com.google.common.truth.Truth.assertThat;

import com.google.idea.blaze.base.BlazeIntegrationTestCase;
import com.google.idea.blaze.base.model.MockBlazeProjectDataBuilder;
import com.google.idea.blaze.base.model.MockBlazeProjectDataManager;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration.BlazeCommandRunConfigurationSettingsEditor;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.sdkcompat.run.RunManagerCompat;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.util.Disposer;
import org.jdom.Element;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Integration tests for {@link BlazeCommandRunConfiguration} saved by {@link
 * com.intellij.execution.impl.RunManagerImpl}.
 */
@RunWith(JUnit4.class)
public class BlazeCommandRunConfigurationRunManagerImplTest extends BlazeIntegrationTestCase {

  private RunManagerImpl runManager;
  private BlazeCommandRunConfigurationType type;
  private BlazeCommandRunConfiguration configuration;

  @Before
  public final void doSetup() {
    runManager = RunManagerImpl.getInstanceImpl(getProject());
    // Without BlazeProjectData, the configuration editor is always disabled.
    BlazeProjectDataManager mockProjectDataManager =
        new MockBlazeProjectDataManager(MockBlazeProjectDataBuilder.builder(workspaceRoot).build());
    registerProjectService(BlazeProjectDataManager.class, mockProjectDataManager);
    type = BlazeCommandRunConfigurationType.getInstance();

    RunnerAndConfigurationSettings runnerAndConfigurationSettings =
        runManager.createConfiguration("Blaze Configuration", type.getFactory());
    runManager.addConfiguration(runnerAndConfigurationSettings, false);
    configuration =
        (BlazeCommandRunConfiguration) runnerAndConfigurationSettings.getConfiguration();
  }

  @After
  public final void doTeardown() {
    runManager.clearAll();

    // Workaround to force commit pending scheme file deletions.  Without this, uncommitted
    // file deletions can occur during later tests and cause unexpected behaviour.
    // This works because runManager.getState() triggers a save operation on SchemeManagerImpl,
    // which commits any pending file operations. (b/127677541)
    runManager.getState();

    // We don't need to do this at setup, because it is handled by RunManagerImpl's constructor.
    // However, clearAll() clears the configuration types, so we need to reinitialize them.
    RunManagerCompat.initializeConfigurationTypes(
        runManager, ConfigurationType.CONFIGURATION_TYPE_EP);
  }

  @Test
  public void loadStateAndGetStateShouldMatch() {
    final Label label = Label.create("//package:rule");
    configuration.setTarget(label);

    final Element element = runManager.getState();
    runManager.loadState(element);
    final RunConfiguration[] configurations = runManager.getAllConfigurations();
    assertThat(configurations).hasLength(1);
    assertThat(configurations[0]).isInstanceOf(BlazeCommandRunConfiguration.class);
    final BlazeCommandRunConfiguration readConfiguration =
        (BlazeCommandRunConfiguration) configurations[0];

    assertThat(readConfiguration.getTarget()).isEqualTo(label);
  }

  @Test
  public void loadStateAndGetStateElementShouldMatch() {
    final XMLOutputter xmlOutputter = new XMLOutputter(Format.getCompactFormat());
    configuration.setTarget(Label.create("//package:rule"));

    final Element initialElement = runManager.getState();
    runManager.loadState(initialElement);
    final Element newElement = runManager.getState();

    assertThat(xmlOutputter.outputString(newElement))
        .isEqualTo(xmlOutputter.outputString(initialElement));
  }

  @Test
  public void loadStateAndGetStateElementShouldMatchAfterChangeAndRevert() {
    final XMLOutputter xmlOutputter = new XMLOutputter(Format.getCompactFormat());
    final Label label = Label.create("//package:rule");
    configuration.setTarget(label);

    final Element initialElement = runManager.getState();
    runManager.loadState(initialElement);
    final BlazeCommandRunConfiguration modifiedConfiguration =
        (BlazeCommandRunConfiguration) runManager.getAllConfigurations()[0];
    modifiedConfiguration.setTarget(Label.create("//new:label"));

    final Element modifiedElement = runManager.getState();
    assertThat(xmlOutputter.outputString(modifiedElement))
        .isNotEqualTo(xmlOutputter.outputString(initialElement));
    runManager.loadState(modifiedElement);
    final BlazeCommandRunConfiguration revertedConfiguration =
        (BlazeCommandRunConfiguration) runManager.getAllConfigurations()[0];
    revertedConfiguration.setTarget(label);

    final Element revertedElement = runManager.getState();
    assertThat(xmlOutputter.outputString(revertedElement))
        .isEqualTo(xmlOutputter.outputString(initialElement));
  }

  @Test
  public void getStateElementShouldMatchAfterEditorApplyToAndResetFrom()
      throws ConfigurationException {
    final XMLOutputter xmlOutputter = new XMLOutputter(Format.getCompactFormat());
    final BlazeCommandRunConfigurationSettingsEditor editor =
        new BlazeCommandRunConfigurationSettingsEditor(configuration);
    configuration.setTarget(Label.create("//package:rule"));

    final Element initialElement = runManager.getState();
    editor.resetFrom(configuration);
    editor.applyEditorTo(configuration);
    final Element newElement = runManager.getState();

    assertThat(xmlOutputter.outputString(newElement))
        .isEqualTo(xmlOutputter.outputString(initialElement));

    Disposer.dispose(editor);
  }
}
