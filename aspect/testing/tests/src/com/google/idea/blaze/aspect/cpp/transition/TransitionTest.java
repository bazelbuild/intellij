/*
 * Copyright 2025 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.aspect.cpp.transition;

import static com.google.common.truth.Truth.assertThat;

import com.google.devtools.intellij.ideinfo.IntellijIdeInfo.TargetIdeInfo;
import com.google.idea.blaze.BazelIntellijAspectTest;
import java.io.IOException;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class TransitionTest extends BazelIntellijAspectTest {

  private List<TargetIdeInfo> getIdeInfos(String targetName) throws IOException {
    final var testFixture = loadTestFixture(":aspect_fixture");

    return findTargets(testFixture, targetName)
        .filter(TargetIdeInfo::hasCIdeInfo)
        .toList();
  }

  @Test
  public void testMultipleIdeInfos() throws IOException {
    final var ideInfos = getIdeInfos(":simple");
    assertThat(ideInfos).hasSize(2);

    for (final var ideInfo : ideInfos) {
      assertThat(ideInfo.getKindString()).isEqualTo("cc_binary");
      assertThat(ideInfo.getKey().getLabel()).isEqualTo("//aspect/testing/tests/src/com/google/idea/blaze/aspect/cpp/transition:simple");
      assertThat(ideInfo.getKey().getConfigurationId()).isNotEmpty();
    }

    assertThat(ideInfos.get(0).getKey().getConfigurationId())
        .isNotEqualTo(ideInfos.get(1).getKey().getConfigurationId());
  }
}
