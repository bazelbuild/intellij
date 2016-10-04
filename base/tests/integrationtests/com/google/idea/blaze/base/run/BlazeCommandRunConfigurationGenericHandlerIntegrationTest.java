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
package com.google.idea.blaze.base.run;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.base.BlazeIntegrationTestCase;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.RuleMap;
import com.google.idea.blaze.base.model.primitives.ExecutionRootPath;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration.BlazeCommandRunConfigurationSettingsEditor;
import com.google.idea.blaze.base.run.confighandler.BlazeCommandGenericRunConfigurationHandler;
import com.google.idea.blaze.base.run.confighandler.BlazeUnknownRunConfigurationHandler;
import com.google.idea.blaze.base.sync.workspace.BlazeRoots;
import com.google.idea.blaze.base.sync.workspace.WorkingSet;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolverImpl;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.util.Disposer;
import org.jdom.Element;

/**
 * Integration tests for {@link BlazeCommandRunConfiguration} with {@link
 * BlazeCommandGenericRunConfigurationHandler} and {@link BlazeUnknownRunConfigurationHandler}.
 */
public class BlazeCommandRunConfigurationGenericHandlerIntegrationTest
    extends BlazeIntegrationTestCase {
  private static final BlazeCommandName COMMAND = BlazeCommandName.fromString("command");

  private BlazeCommandRunConfigurationType type;
  private BlazeCommandRunConfiguration configuration;

  @Override
  protected void doSetup() throws Exception {
    super.doSetup();
    // Without BlazeProjectData, the configuration editor is always disabled.
    mockBlazeProjectDataManager(getMockBlazeProjectData());
    type = BlazeCommandRunConfigurationType.getInstance();
    configuration = type.getFactory().createTemplateConfiguration(getProject());
  }

  private BlazeProjectData getMockBlazeProjectData() {
    BlazeRoots fakeRoots =
        new BlazeRoots(
            null,
            ImmutableList.of(workspaceRoot.directory()),
            new ExecutionRootPath("out/crosstool/bin"),
            new ExecutionRootPath("out/crosstool/gen"));
    return new BlazeProjectData(
        0,
        new RuleMap(ImmutableMap.of()),
        fakeRoots,
        new WorkingSet(ImmutableList.of(), ImmutableList.of(), ImmutableList.of()),
        new WorkspacePathResolverImpl(workspaceRoot, fakeRoots),
        null,
        null,
        null,
        null);
  }

  public void testNewConfigurationHasUnknownHandler() {
    assertThat(configuration.getHandler()).isInstanceOf(BlazeUnknownRunConfigurationHandler.class);
  }

  public void testSetTargetNullMakesGenericHandler() {
    configuration.setTarget(null);
    assertThat(configuration.getHandler())
        .isInstanceOf(BlazeCommandGenericRunConfigurationHandler.class);
  }

  public void testTargetExpressionMakesGenericHandler() {
    configuration.setTarget(TargetExpression.fromString("//..."));
    assertThat(configuration.getHandler())
        .isInstanceOf(BlazeCommandGenericRunConfigurationHandler.class);
  }

  public void testReadAndWriteMatches() throws Exception {
    TargetExpression targetExpression = TargetExpression.fromString("//...");
    configuration.setTarget(targetExpression);

    BlazeCommandGenericRunConfigurationHandler handler =
        (BlazeCommandGenericRunConfigurationHandler) configuration.getHandler();
    handler.setCommand(COMMAND);
    handler.setBlazeFlags(ImmutableList.of("--flag1", "--flag2"));
    handler.setExeFlags(ImmutableList.of("--exeFlag1"));
    handler.setBlazeBinary("/usr/bin/blaze");

    Element element = new Element("test");
    configuration.writeExternal(element);
    BlazeCommandRunConfiguration readConfiguration =
        type.getFactory().createTemplateConfiguration(getProject());
    readConfiguration.readExternal(element);

    assertThat(readConfiguration.getTarget()).isEqualTo(targetExpression);
    assertThat(readConfiguration.getHandler())
        .isInstanceOf(BlazeCommandGenericRunConfigurationHandler.class);

    BlazeCommandGenericRunConfigurationHandler readHandler =
        (BlazeCommandGenericRunConfigurationHandler) readConfiguration.getHandler();
    assertThat(readHandler.getCommand()).isEqualTo(COMMAND);
    assertThat(readHandler.getAllBlazeFlags()).containsExactly("--flag1", "--flag2").inOrder();
    assertThat(readHandler.getAllExeFlags()).containsExactly("--exeFlag1");
    assertThat(readHandler.getBlazeBinary()).isEqualTo("/usr/bin/blaze");
  }

  public void testReadAndWriteHandlesNulls() throws Exception {
    Element element = new Element("test");
    configuration.writeExternal(element);
    BlazeCommandRunConfiguration readConfiguration =
        type.getFactory().createTemplateConfiguration(getProject());
    readConfiguration.readExternal(element);

    assertThat(readConfiguration.getTarget()).isEqualTo(configuration.getTarget());
    assertThat(readConfiguration.getHandler())
        .isInstanceOf(BlazeUnknownRunConfigurationHandler.class);
  }

  public void testEditorWithUnknownHandlerDoesNotApplyTo() throws ConfigurationException {
    assertThat(configuration.getTarget()).isNull();
    assertThat(configuration.getHandler()).isInstanceOf(BlazeUnknownRunConfigurationHandler.class);

    BlazeCommandRunConfigurationSettingsEditor editor =
        new BlazeCommandRunConfigurationSettingsEditor(configuration);
    // Because the configuration's handler is BlazeUnknownRunConfigurationHandler,
    // resetting the editor to it will leave it in the disabled state.
    editor.resetFrom(configuration);

    BlazeCommandRunConfiguration readConfiguration =
        type.getFactory().createTemplateConfiguration(getProject());
    TargetExpression targetExpression = TargetExpression.fromString("//...");
    readConfiguration.setTarget(targetExpression);

    BlazeCommandGenericRunConfigurationHandler readHandler =
        (BlazeCommandGenericRunConfigurationHandler) readConfiguration.getHandler();
    readHandler.setCommand(COMMAND);
    readHandler.setBlazeFlags(ImmutableList.of("--flag1", "--flag2"));
    readHandler.setExeFlags(ImmutableList.of("--exeFlag1"));
    readHandler.setBlazeBinary("/usr/bin/blaze");

    // The editor is disabled, making applyEditorTo a no-op.
    editor.applyEditorTo(readConfiguration);

    assertThat(readConfiguration.getTarget()).isEqualTo(targetExpression);
    assertThat(readConfiguration.getHandler())
        .isInstanceOf(BlazeCommandGenericRunConfigurationHandler.class);

    readHandler = (BlazeCommandGenericRunConfigurationHandler) readConfiguration.getHandler();
    assertThat(readHandler.getCommand()).isEqualTo(COMMAND);
    assertThat(readHandler.getAllBlazeFlags()).containsExactly("--flag1", "--flag2").inOrder();
    assertThat(readHandler.getAllExeFlags()).containsExactly("--exeFlag1");
    assertThat(readHandler.getBlazeBinary()).isEqualTo("/usr/bin/blaze");

    Disposer.dispose(editor);
  }

  public void testEditorApplyToAndResetFromMatches() throws ConfigurationException {
    BlazeCommandRunConfigurationSettingsEditor editor =
        new BlazeCommandRunConfigurationSettingsEditor(configuration);
    TargetExpression targetExpression = TargetExpression.fromString("//...");
    configuration.setTarget(targetExpression);

    BlazeCommandGenericRunConfigurationHandler handler =
        (BlazeCommandGenericRunConfigurationHandler) configuration.getHandler();
    handler.setCommand(COMMAND);
    handler.setBlazeFlags(ImmutableList.of("--flag1", "--flag2"));
    handler.setExeFlags(ImmutableList.of("--exeFlag1"));
    handler.setBlazeBinary("/usr/bin/blaze");

    editor.resetFrom(configuration);
    BlazeCommandRunConfiguration readConfiguration =
        type.getFactory().createTemplateConfiguration(getProject());
    editor.applyEditorTo(readConfiguration);

    assertThat(readConfiguration.getTarget()).isEqualTo(targetExpression);
    assertThat(readConfiguration.getHandler())
        .isInstanceOf(BlazeCommandGenericRunConfigurationHandler.class);

    BlazeCommandGenericRunConfigurationHandler readHandler =
        (BlazeCommandGenericRunConfigurationHandler) readConfiguration.getHandler();
    assertThat(readHandler.getCommand()).isEqualTo(handler.getCommand());
    assertThat(readHandler.getAllBlazeFlags()).isEqualTo(handler.getAllBlazeFlags());
    assertThat(readHandler.getAllExeFlags()).isEqualTo(handler.getAllExeFlags());
    assertThat(readHandler.getBlazeBinary()).isEqualTo(handler.getBlazeBinary());

    Disposer.dispose(editor);
  }

  public void testEditorApplyToAndResetFromHandlesNulls() throws ConfigurationException {
    BlazeCommandRunConfigurationSettingsEditor editor =
        new BlazeCommandRunConfigurationSettingsEditor(configuration);

    // Call setTarget to initialize a generic handler, or this won't apply anything.
    configuration.setTarget(null);
    assertThat(configuration.getTarget()).isNull();
    assertThat(configuration.getHandler())
        .isInstanceOf(BlazeCommandGenericRunConfigurationHandler.class);
    BlazeCommandGenericRunConfigurationHandler handler =
        (BlazeCommandGenericRunConfigurationHandler) configuration.getHandler();

    editor.resetFrom(configuration);

    BlazeCommandRunConfiguration readConfiguration =
        type.getFactory().createTemplateConfiguration(getProject());
    TargetExpression targetExpression = TargetExpression.fromString("//...");
    readConfiguration.setTarget(targetExpression);

    BlazeCommandGenericRunConfigurationHandler readHandler =
        (BlazeCommandGenericRunConfigurationHandler) readConfiguration.getHandler();
    readHandler.setCommand(COMMAND);
    readHandler.setBlazeFlags(ImmutableList.of("--flag1", "--flag2"));
    readHandler.setExeFlags(ImmutableList.of("--exeFlag1"));
    readHandler.setBlazeBinary("/usr/bin/blaze");

    editor.applyEditorTo(readConfiguration);

    assertThat(readConfiguration.getTarget()).isNull();
    assertThat(configuration.getHandler())
        .isInstanceOf(BlazeCommandGenericRunConfigurationHandler.class);

    readHandler = (BlazeCommandGenericRunConfigurationHandler) readConfiguration.getHandler();
    assertThat(readHandler.getCommand()).isEqualTo(handler.getCommand());
    assertThat(readHandler.getAllBlazeFlags()).isEqualTo(handler.getAllBlazeFlags());
    assertThat(readHandler.getAllExeFlags()).isEqualTo(handler.getAllExeFlags());
    assertThat(readHandler.getBlazeBinary()).isEqualTo(handler.getBlazeBinary());

    Disposer.dispose(editor);
  }
}
