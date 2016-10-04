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
import com.google.common.collect.Lists;
import com.google.idea.blaze.android.cppapi.NdkSupport;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.common.experiments.ExperimentService;
import com.google.idea.common.experiments.MockExperimentService;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link BlazeAndroidRunConfigurationCommonState}. */
@RunWith(JUnit4.class)
public class BlazeAndroidRunConfigurationCommonStateTest extends BlazeTestCase {
  private BlazeAndroidRunConfigurationCommonState commonState;

  @Override
  protected void initTest(
      @NotNull Container applicationServices, @NotNull Container projectServices) {
    super.initTest(applicationServices, projectServices);

    MockExperimentService experimentService = new MockExperimentService();
    applicationServices.register(ExperimentService.class, experimentService);
    // BlazeAndroidRunConfigurationCommonState.isNativeDebuggingEnabled() always
    // returns false if this experiment is false.
    experimentService.setExperiment(NdkSupport.NDK_SUPPORT, true);

    commonState = new BlazeAndroidRunConfigurationCommonState(ImmutableList.of());
  }

  @Test
  public void readAndWriteShouldMatch() throws InvalidDataException, WriteExternalException {
    commonState.setUserFlags(ImmutableList.of("--flag1", "--flag2"));
    commonState.setNativeDebuggingEnabled(true);

    Element element = new Element("test");
    commonState.writeExternal(element);
    BlazeAndroidRunConfigurationCommonState readCommonState =
        new BlazeAndroidRunConfigurationCommonState(ImmutableList.of());
    readCommonState.readExternal(element);

    assertThat(readCommonState.getUserFlags()).containsExactly("--flag1", "--flag2").inOrder();
    assertThat(readCommonState.isNativeDebuggingEnabled()).isTrue();
  }

  @Test
  public void readAndWriteShouldHandleNulls() throws InvalidDataException, WriteExternalException {
    Element element = new Element("test");
    commonState.writeExternal(element);
    BlazeAndroidRunConfigurationCommonState readCommonState =
        new BlazeAndroidRunConfigurationCommonState(ImmutableList.of());
    readCommonState.readExternal(element);

    assertThat(readCommonState.getUserFlags()).isEqualTo(commonState.getUserFlags());
    assertThat(readCommonState.isNativeDebuggingEnabled())
        .isEqualTo(commonState.isNativeDebuggingEnabled());
  }

  @Test
  public void readShouldOmitEmptyFlags() throws InvalidDataException, WriteExternalException {
    commonState.setUserFlags(Lists.newArrayList("hi ", "", "I'm", " ", "\t", "Josh\r\n", "\n"));

    Element element = new Element("test");
    commonState.writeExternal(element);
    BlazeAndroidRunConfigurationCommonState readCommonState =
        new BlazeAndroidRunConfigurationCommonState(ImmutableList.of());
    readCommonState.readExternal(element);

    assertThat(readCommonState.getUserFlags()).containsExactly("hi", "I'm", "Josh").inOrder();
  }
}
