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
package com.google.idea.blaze.aspect.cpp.cctest;

import static com.google.common.truth.Truth.assertThat;

import com.google.devtools.intellij.IntellijAspectTestFixtureOuterClass.IntellijAspectTestFixture;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo.CIdeInfo;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo.TargetIdeInfo;
import com.google.idea.blaze.BazelIntellijAspectTest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests cc_test */
@RunWith(JUnit4.class)
public class CcTestTest extends BazelIntellijAspectTest {
  @Test
  public void testCcTest() throws Exception {
    IntellijAspectTestFixture testFixture = loadTestFixture(":simple_fixture");
    TargetIdeInfo target = findTarget(testFixture, ":simple");
    assertThat(target.getKindString()).isEqualTo("cc_test");

    assertThat(target.hasCIdeInfo()).isTrue();
    assertThat(target.hasJavaIdeInfo()).isFalse();
    assertThat(target.hasAndroidIdeInfo()).isFalse();
    CIdeInfo cTargetIdeInfo = target.getCIdeInfo();

    assertThat(cTargetIdeInfo.getTargetCoptList()).isEmpty();

    // Can't test for this because the cc code stuffs source
    // artifacts into the output group
    // assertThat(testFixture.getIntellijResolveFilesList()).isEmpty();
    assertThat(getOutputGroupFiles(testFixture, "intellij-info-cpp"))
        .contains(testRelative(intellijInfoFileName("simple")));
    assertThat(getOutputGroupFiles(testFixture, "intellij-info-generic")).isEmpty();
  }
}
