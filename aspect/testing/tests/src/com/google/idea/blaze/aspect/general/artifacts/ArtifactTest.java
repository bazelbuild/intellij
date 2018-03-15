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
package com.google.idea.blaze.aspect.general.artifacts;

import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.truth.Truth.assertThat;

import com.google.devtools.intellij.IntellijAspectTestFixtureOuterClass.IntellijAspectTestFixture;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo.TargetIdeInfo;
import com.google.idea.blaze.BazelIntellijAspectTest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests the artifact representation */
@RunWith(JUnit4.class)
public class ArtifactTest extends BazelIntellijAspectTest {
  @Test
  public void testSourceFilesAreCorrectlyMarkedAsSourceOrGenerated() throws Exception {
    IntellijAspectTestFixture testFixture = loadTestFixture(":gen_sources_fixture");
    TargetIdeInfo source = findTarget(testFixture, ":source");
    TargetIdeInfo gen = findTarget(testFixture, ":gen");
    assertThat(getOnlyElement(source.getJavaIdeInfo().getSourcesList()).getIsSource()).isTrue();
    assertThat(getOnlyElement(gen.getJavaIdeInfo().getSourcesList()).getIsSource()).isFalse();
  }
}
