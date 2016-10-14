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
import com.google.idea.blaze.base.ideinfo.RuleMap;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.ExecutionRootPath;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration.BlazeCommandRunConfigurationSettingsEditor;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoderImpl;
import com.google.idea.blaze.base.sync.workspace.BlazeRoots;
import com.google.idea.blaze.base.sync.workspace.WorkingSet;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolverImpl;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.util.Disposer;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link BlazeCommandRunConfiguration.BlazeCommandRunConfigurationSettingsEditor}. */
@RunWith(JUnit4.class)
public class BlazeCommandRunConfigurationSettingsEditorTest extends BlazeIntegrationTestCase {

  private BlazeCommandRunConfigurationType type;
  private BlazeCommandRunConfiguration configuration;

  @Before
  public final void doSetup() throws Exception {
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
    WorkspacePathResolver workspacePathResolver =
        new WorkspacePathResolverImpl(workspaceRoot, fakeRoots);
    ArtifactLocationDecoder artifactLocationDecoder =
        new ArtifactLocationDecoderImpl(fakeRoots, workspacePathResolver);
    return new BlazeProjectData(
        0,
        new RuleMap(ImmutableMap.of()),
        fakeRoots,
        new WorkingSet(ImmutableList.of(), ImmutableList.of(), ImmutableList.of()),
        workspacePathResolver,
        artifactLocationDecoder,
        null,
        null,
        null,
        null);
  }

  @Test
  public void testEditorApplyToAndResetFromMatches() throws ConfigurationException {
    BlazeCommandRunConfigurationSettingsEditor editor =
        new BlazeCommandRunConfigurationSettingsEditor(configuration);
    Label label = new Label("//package:rule");
    configuration.setTarget(label);

    editor.resetFrom(configuration);
    BlazeCommandRunConfiguration readConfiguration =
        type.getFactory().createTemplateConfiguration(getProject());
    editor.applyEditorTo(readConfiguration);

    assertThat(readConfiguration.getTarget()).isEqualTo(label);

    Disposer.dispose(editor);
  }

  @Test
  public void testEditorApplyToAndResetFromHandlesNulls() throws ConfigurationException {
    BlazeCommandRunConfigurationSettingsEditor editor =
        new BlazeCommandRunConfigurationSettingsEditor(configuration);

    editor.resetFrom(configuration);
    BlazeCommandRunConfiguration readConfiguration =
        type.getFactory().createTemplateConfiguration(getProject());
    editor.applyEditorTo(readConfiguration);

    assertThat(readConfiguration.getTarget()).isEqualTo(configuration.getTarget());

    Disposer.dispose(editor);
  }
}
