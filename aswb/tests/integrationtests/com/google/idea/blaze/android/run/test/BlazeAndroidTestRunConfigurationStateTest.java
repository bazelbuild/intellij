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
package com.google.idea.blaze.android.run.test;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.android.AndroidIntegrationTestCleanupHelper;
import com.google.idea.blaze.android.AndroidIntegrationTestSetupRule;
import com.google.idea.blaze.android.cppapi.NdkSupport;
import com.google.idea.blaze.android.run.BlazeAndroidRunConfigurationCommonState;
import com.google.idea.blaze.base.BlazeIntegrationTestCase;
import com.google.idea.blaze.base.run.state.RunConfigurationStateEditor;
import com.google.idea.common.experiments.ExperimentService;
import com.google.idea.common.experiments.MockExperimentService;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link BlazeAndroidTestRunConfigurationState}. */
@RunWith(JUnit4.class)
public class BlazeAndroidTestRunConfigurationStateTest extends BlazeIntegrationTestCase {

  @Rule
  public final AndroidIntegrationTestSetupRule androidSetupRule =
      new AndroidIntegrationTestSetupRule();
  private BlazeAndroidTestRunConfigurationState state;

  @Before
  public final void doSetup() {
    MockExperimentService experimentService = new MockExperimentService();
    registerApplicationComponent(ExperimentService.class, experimentService);
    // BlazeAndroidRunConfigurationCommonState.isNativeDebuggingEnabled() always
    // returns false if this experiment is false.
    experimentService.setExperiment(NdkSupport.NDK_SUPPORT, true);

    state = new BlazeAndroidTestRunConfigurationState(buildSystem().getName());
  }

  @After
  public final void doTeardown() {
    AndroidIntegrationTestCleanupHelper.cleanUp(getProject());
  }

  @Test
  public void readAndWriteShouldMatch() throws InvalidDataException, WriteExternalException {
    BlazeAndroidRunConfigurationCommonState commonState = state.getCommonState();
    commonState.setUserFlags(ImmutableList.of("--flag1", "--flag2"));
    commonState.setNativeDebuggingEnabled(true);

    state.setTestingType(BlazeAndroidTestRunConfigurationState.TEST_METHOD);
    state.setInstrumentationRunnerClass("com.example.TestRunner");
    state.setMethodName("fooMethod");
    state.setClassName("BarClass");
    state.setPackageName("com.test.package.name");
    state.setRunThroughBlaze(true);
    state.setExtraOptions("--option");

    Element element = new Element("test");
    state.writeExternal(element);
    BlazeAndroidTestRunConfigurationState readState =
        new BlazeAndroidTestRunConfigurationState(buildSystem().getName());
    readState.readExternal(element);

    BlazeAndroidRunConfigurationCommonState readCommonState = readState.getCommonState();
    assertThat(readCommonState.getUserFlags()).containsExactly("--flag1", "--flag2").inOrder();
    assertThat(readCommonState.isNativeDebuggingEnabled()).isTrue();

    assertThat(readState.getTestingType())
        .isEqualTo(BlazeAndroidTestRunConfigurationState.TEST_METHOD);
    assertThat(readState.getInstrumentationRunnerClass()).isEqualTo("com.example.TestRunner");
    assertThat(readState.getMethodName()).isEqualTo("fooMethod");
    assertThat(readState.getClassName()).isEqualTo("BarClass");
    assertThat(readState.getPackageName()).isEqualTo("com.test.package.name");
    assertThat(readState.isRunThroughBlaze()).isTrue();
    assertThat(readState.getExtraOptions()).isEqualTo("--option");
  }

  @Test
  public void readAndWriteShouldHandleNulls() throws InvalidDataException, WriteExternalException {
    Element element = new Element("test");
    state.writeExternal(element);
    BlazeAndroidTestRunConfigurationState readState =
        new BlazeAndroidTestRunConfigurationState(buildSystem().getName());
    readState.readExternal(element);

    BlazeAndroidRunConfigurationCommonState commonState = state.getCommonState();
    BlazeAndroidRunConfigurationCommonState readCommonState = readState.getCommonState();
    assertThat(readCommonState.getUserFlags()).isEqualTo(commonState.getUserFlags());
    assertThat(readCommonState.isNativeDebuggingEnabled())
        .isEqualTo(commonState.isNativeDebuggingEnabled());

    assertThat(readState.getTestingType()).isEqualTo(state.getTestingType());
    assertThat(readState.getInstrumentationRunnerClass())
        .isEqualTo(state.getInstrumentationRunnerClass());
    assertThat(readState.getMethodName()).isEqualTo(state.getMethodName());
    assertThat(readState.getClassName()).isEqualTo(state.getClassName());
    assertThat(readState.getPackageName()).isEqualTo(state.getPackageName());
    assertThat(readState.isRunThroughBlaze()).isEqualTo(state.isRunThroughBlaze());
    assertThat(readState.getExtraOptions()).isEqualTo(state.getExtraOptions());
  }

  @Test
  public void repeatedWriteShouldNotChangeElement() throws WriteExternalException {
    final XMLOutputter xmlOutputter = new XMLOutputter(Format.getCompactFormat());

    BlazeAndroidRunConfigurationCommonState commonState = state.getCommonState();
    commonState.setUserFlags(ImmutableList.of("--flag1", "--flag2"));
    commonState.setNativeDebuggingEnabled(true);

    state.setTestingType(BlazeAndroidTestRunConfigurationState.TEST_METHOD);
    state.setInstrumentationRunnerClass("com.example.TestRunner");
    state.setMethodName("fooMethod");
    state.setClassName("BarClass");
    state.setPackageName("com.test.package.name");
    state.setRunThroughBlaze(true);
    state.setExtraOptions("--option");

    Element firstWrite = new Element("test");
    state.writeExternal(firstWrite);
    Element secondWrite = firstWrite.clone();
    state.writeExternal(secondWrite);

    assertThat(xmlOutputter.outputString(secondWrite))
        .isEqualTo(xmlOutputter.outputString(firstWrite));
  }

  @Test
  public void editorApplyToAndResetFromShouldMatch() throws ConfigurationException {
    RunConfigurationStateEditor editor = state.getEditor(getProject());

    BlazeAndroidRunConfigurationCommonState commonState = state.getCommonState();
    commonState.setUserFlags(ImmutableList.of("--flag1", "--flag2"));
    commonState.setNativeDebuggingEnabled(true);

    state.setTestingType(BlazeAndroidTestRunConfigurationState.TEST_METHOD);
    state.setInstrumentationRunnerClass("com.example.TestRunner");
    state.setMethodName("fooMethod");
    state.setClassName("BarClass");
    state.setPackageName("com.test.package.name");
    state.setRunThroughBlaze(true);
    // We don't test ExtraOptions because it is not exposed in the editor.
    //state.setExtraOptions("--option");

    editor.resetEditorFrom(state);
    BlazeAndroidTestRunConfigurationState readState =
        new BlazeAndroidTestRunConfigurationState(buildSystem().getName());
    editor.applyEditorTo(readState);

    BlazeAndroidRunConfigurationCommonState readCommonState = readState.getCommonState();
    assertThat(readCommonState.getUserFlags()).isEqualTo(commonState.getUserFlags());
    assertThat(readCommonState.isNativeDebuggingEnabled())
        .isEqualTo(commonState.isNativeDebuggingEnabled());

    assertThat(readState.getTestingType()).isEqualTo(state.getTestingType());
    assertThat(readState.getInstrumentationRunnerClass())
        .isEqualTo(state.getInstrumentationRunnerClass());
    assertThat(readState.getMethodName()).isEqualTo(state.getMethodName());
    assertThat(readState.getClassName()).isEqualTo(state.getClassName());
    assertThat(readState.getPackageName()).isEqualTo(state.getPackageName());
    assertThat(readState.isRunThroughBlaze()).isEqualTo(state.isRunThroughBlaze());
    // We don't test ExtraOptions because it is not exposed in the editor.
    //assertThat(readState.getExtraOptions()).isEqualTo(state.getExtraOptions());
  }

  @Test
  public void editorApplyToAndResetFromShouldHandleNulls() throws ConfigurationException {
    RunConfigurationStateEditor editor = state.getEditor(getProject());

    editor.resetEditorFrom(state);
    BlazeAndroidTestRunConfigurationState readState =
        new BlazeAndroidTestRunConfigurationState(buildSystem().getName());
    editor.applyEditorTo(readState);

    BlazeAndroidRunConfigurationCommonState commonState = state.getCommonState();
    BlazeAndroidRunConfigurationCommonState readCommonState = readState.getCommonState();
    assertThat(readCommonState.getUserFlags()).isEqualTo(commonState.getUserFlags());
    assertThat(readCommonState.isNativeDebuggingEnabled())
        .isEqualTo(commonState.isNativeDebuggingEnabled());

    assertThat(readState.getTestingType()).isEqualTo(state.getTestingType());
    assertThat(readState.getInstrumentationRunnerClass())
        .isEqualTo(state.getInstrumentationRunnerClass());
    assertThat(readState.getMethodName()).isEqualTo(state.getMethodName());
    assertThat(readState.getClassName()).isEqualTo(state.getClassName());
    assertThat(readState.getPackageName()).isEqualTo(state.getPackageName());
    assertThat(readState.isRunThroughBlaze()).isEqualTo(state.isRunThroughBlaze());
    // We don't test ExtraOptions because it is not exposed in the editor.
    //assertThat(readState.getExtraOptions()).isEqualTo(state.getExtraOptions());
  }
}
