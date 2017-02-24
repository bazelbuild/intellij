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

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.android.AndroidIntegrationTestCleanupHelper;
import com.google.idea.blaze.android.AndroidIntegrationTestSetupRule;
import com.google.idea.blaze.android.cppapi.NdkSupport;
import com.google.idea.blaze.base.BlazeIntegrationTestCase;
import com.google.idea.common.experiments.ExperimentService;
import com.google.idea.common.experiments.MockExperimentService;
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

/** Tests for {@link BlazeAndroidRunConfigurationCommonState}. */
@RunWith(JUnit4.class)
public class BlazeAndroidRunConfigurationCommonStateTest extends BlazeIntegrationTestCase {

  @Rule
  public final AndroidIntegrationTestSetupRule androidSetupRule =
      new AndroidIntegrationTestSetupRule();
  private BlazeAndroidRunConfigurationCommonState state;

  @Before
  public final void doSetup() {
    MockExperimentService experimentService = new MockExperimentService();
    registerApplicationComponent(ExperimentService.class, experimentService);
    // BlazeAndroidRunConfigurationCommonState.isNativeDebuggingEnabled() always
    // returns false if this experiment is false.
    experimentService.setExperiment(NdkSupport.NDK_SUPPORT, true);

    state = new BlazeAndroidRunConfigurationCommonState(buildSystem().getName(), false);
  }

  @After
  public final void doTeardown() {
    AndroidIntegrationTestCleanupHelper.cleanUp(getProject());
  }

  @Test
  public void readAndWriteShouldMatch() throws InvalidDataException, WriteExternalException {
    state.setUserFlags(ImmutableList.of("--flag1", "--flag2"));
    state.setNativeDebuggingEnabled(true);

    Element element = new Element("test");
    state.writeExternal(element);
    BlazeAndroidRunConfigurationCommonState readState =
        new BlazeAndroidRunConfigurationCommonState(buildSystem().getName(), false);
    readState.readExternal(element);

    assertThat(readState.getUserFlags()).containsExactly("--flag1", "--flag2").inOrder();
    assertThat(readState.isNativeDebuggingEnabled()).isTrue();
  }

  @Test
  public void readAndWriteShouldHandleNulls() throws InvalidDataException, WriteExternalException {
    Element element = new Element("test");
    state.writeExternal(element);
    BlazeAndroidRunConfigurationCommonState readState =
        new BlazeAndroidRunConfigurationCommonState(buildSystem().getName(), false);
    readState.readExternal(element);

    assertThat(readState.getUserFlags()).isEqualTo(state.getUserFlags());
    assertThat(readState.isNativeDebuggingEnabled()).isEqualTo(state.isNativeDebuggingEnabled());
  }

  @Test
  public void readShouldOmitEmptyFlags() throws InvalidDataException, WriteExternalException {
    state.setUserFlags(ImmutableList.of("hi ", "", "I'm", " ", "\t", "Josh\r\n", "\n"));

    Element element = new Element("test");
    state.writeExternal(element);
    BlazeAndroidRunConfigurationCommonState readState =
        new BlazeAndroidRunConfigurationCommonState(buildSystem().getName(), false);
    readState.readExternal(element);

    assertThat(readState.getUserFlags()).containsExactly("hi", "I'm", "Josh").inOrder();
  }

  @Test
  public void repeatedWriteShouldNotChangeElement() throws WriteExternalException {
    final XMLOutputter xmlOutputter = new XMLOutputter(Format.getCompactFormat());

    state.setUserFlags(ImmutableList.of("--flag1", "--flag2"));
    state.setNativeDebuggingEnabled(true);

    Element firstWrite = new Element("test");
    state.writeExternal(firstWrite);
    Element secondWrite = firstWrite.clone();
    state.writeExternal(secondWrite);

    assertThat(xmlOutputter.outputString(secondWrite))
        .isEqualTo(xmlOutputter.outputString(firstWrite));
  }
}
