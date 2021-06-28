/*
 * Copyright 2021 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.aspect.general.analysistest;

import static com.google.common.truth.Truth.assertThat;

import com.google.devtools.intellij.IntellijAspectTestFixtureOuterClass.IntellijAspectTestFixture;
import com.google.idea.blaze.BazelIntellijAspectTest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests that analysis_test rules are skipped */
@RunWith(JUnit4.class)
public class AnalysisTestTest extends BazelIntellijAspectTest {
  @Test
  public void testAnalysisTest() throws Exception {
    IntellijAspectTestFixture testFixture = loadTestFixture(":analysis_test_fixture");
    assertThat(findTarget(testFixture, ":noop_analysis_test")).isNull();
    assertThat(findTarget(testFixture, ":foo")).isNotNull();
  }
}
