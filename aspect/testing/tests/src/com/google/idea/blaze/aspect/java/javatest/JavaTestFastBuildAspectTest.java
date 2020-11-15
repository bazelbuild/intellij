/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.aspect.java.javatest;

import static com.google.common.truth.Truth.assertThat;

import com.google.devtools.intellij.aspect.FastBuildAspectTestFixtureOuterClass.FastBuildAspectTestFixture;
import com.google.devtools.intellij.aspect.FastBuildInfo.FastBuildBlazeData;
import com.google.idea.blaze.aspect.FastBuildAspectRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for the fast build aspect. */
@RunWith(JUnit4.class)
public final class JavaTestFastBuildAspectTest {

  private static final String NO_LAUNCHER_TARGET = "";

  @Rule
  public FastBuildAspectRule aspectLoader =
      new FastBuildAspectRule("aspect/testing/tests/src");

  @Test
  public void testNoJavaLauncherSpecified() throws Exception {
    FastBuildAspectTestFixture fixture =
        aspectLoader.loadTestFixture(":footest_no_launcher_fast_build_fixture");
    FastBuildBlazeData data =
        getDataForTarget(aspectLoader.testRelative(":FooTestNoLauncher"), fixture);
    assertThat(data.getJavaInfo().getLauncher()).isEqualTo(NO_LAUNCHER_TARGET);
  }

  @Test
  public void testCustomJavaLauncher() throws Exception {
    FastBuildAspectTestFixture fixture =
        aspectLoader.loadTestFixture(":footest_with_custom_launcher_fast_build_fixture");
    FastBuildBlazeData data =
        getDataForTarget(aspectLoader.testRelative(":FooTestWithCustomLauncher"), fixture);
    assertThat(data.getJavaInfo().getLauncher())
        .isEqualTo(aspectLoader.testRelative(":custom_java_launcher"));
  }


  private FastBuildBlazeData getDataForTarget(String target, FastBuildAspectTestFixture fixture) {
    return fixture.getTargetList().stream()
        .filter(data -> data.getLabel().equals(target))
        .findFirst()
        .get();
  }
}
