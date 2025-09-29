/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.aspect.cpp.ccbinary;

import static com.google.common.truth.Truth.assertThat;

import com.google.idea.blaze.BazelIntellijAspectTest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CcBinaryTest extends BazelIntellijAspectTest {

  @Test
  public void testCcBinary() throws Exception {
    final var testFixture = loadTestFixture(":aspect_fixture");
    final var target = findTarget(testFixture, ":simple");
    assertThat(target.getKindString()).isEqualTo("cc_binary");

    assertThat(target.hasCIdeInfo()).isTrue();
    assertThat(target.hasJavaIdeInfo()).isFalse();
    assertThat(target.hasAndroidIdeInfo()).isFalse();
    final var cTargetIdeInfo = target.getCIdeInfo();

    assertThat(cTargetIdeInfo.getRuleContext().getCoptsList()).isEmpty();

    assertThat(getOutputGroupFiles(testFixture, "intellij-resolve-cpp")).isEmpty();
    assertThat(getOutputGroupFiles(testFixture, "intellij-info-generic")).isEmpty();

    assertThat(getOutputGroupFiles(testFixture, "intellij-info-cpp")).contains(
        testRelative(intellijInfoFileName("simple")));
  }

  @Test
  public void testExpandDataDeps() throws Exception {
    final var testFixture = loadTestFixture(":aspect_fixture");
    final var target = findTarget(testFixture, ":expand_datadeps");
    assertThat(target.getKindString()).isEqualTo("cc_binary");

    final var args = target.getCIdeInfo().getRuleContext().getArgsList();
    assertThat(args).hasSize(1);
    assertThat(args.get(0)).endsWith(
        "/aspect/testing/tests/src/com/google/idea/blaze/aspect/cpp/ccbinary/datadepfile.txt");
  }
}
