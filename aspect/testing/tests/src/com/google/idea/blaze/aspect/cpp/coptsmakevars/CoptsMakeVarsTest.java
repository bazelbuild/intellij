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
package com.google.idea.blaze.aspect.cpp.coptsmakevars;

import static com.google.common.truth.Truth.assertThat;

import com.google.devtools.intellij.ideinfo.IntellijIdeInfo.CIdeInfo;
import com.google.idea.blaze.BazelIntellijAspectTest;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CoptsMakeVarsTest extends BazelIntellijAspectTest {

  private CIdeInfo getCIdeInfo(String targetName) throws IOException {
    final var testFixture = loadTestFixture(":aspect_fixture");
    final var target = findTarget(testFixture, targetName);
    assertThat(target.getKindString()).isEqualTo("cc_binary");
    assertThat(target.hasCIdeInfo()).isTrue();

    return target.getCIdeInfo();
  }

  @Test
  public void testCoptsPrefinedMakeVars() throws IOException {
    final var ideInfo = getCIdeInfo(":simple_prefined");
    assertThat(ideInfo.getTargetCoptList()).hasSize(2);

    // These predefined variables' values are dependent on build system and configuration.
    assertThat(ideInfo.getTargetCoptList().get(0)).containsMatch("^-DPREFINED_BINDIR=bazel-out/[0-9a-z_-]+/bin$");
    assertThat(ideInfo.getTargetCoptList().get(1)).isEqualTo("-DPREFINED_BINDIR2=$(BINDIR)");
  }

  @Test
  public void testCoptsEmptyVariable() throws IOException {
    final var ideInfo = getCIdeInfo(":empty_variable");
    assertThat(ideInfo.getTargetCoptList()).hasSize(1);
    assertThat(ideInfo.getTargetCoptList()).contains("-Wall");
  }

  @Test
  public void testCoptsMakeVars() throws IOException {
    final var ideInfo = getCIdeInfo(":simple_make_var");
    assertThat(ideInfo.getTargetCoptList()).hasSize(4);

    assertThat(ideInfo.getTargetCopt(0)).isEqualTo(
        "-DEXECPATH=\"aspect/testing/tests/src/com/google/idea/blaze/aspect/cpp/coptsmakevars/simple/simple.cc\"");
    assertThat(ideInfo.getTargetCopt(1)).isEqualTo(
        "-DROOTPATH=\"aspect/testing/tests/src/com/google/idea/blaze/aspect/cpp/coptsmakevars/simple/simple.cc\"");
    assertThat(ideInfo.getTargetCopt(2)).isEqualTo(
        "-DRLOCATIONPATH=\"_main/aspect/testing/tests/src/com/google/idea/blaze/aspect/cpp/coptsmakevars/simple/simple.cc\"");
    assertThat(ideInfo.getTargetCopt(3)).isEqualTo(
        "-DLOCATION=\"aspect/testing/tests/src/com/google/idea/blaze/aspect/cpp/coptsmakevars/simple/simple.cc\"");
  }
}
