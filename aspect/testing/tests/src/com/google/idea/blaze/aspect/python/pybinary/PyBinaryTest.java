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
package com.google.idea.blaze.aspect.python.pybinary;

import static com.google.common.truth.Truth.assertThat;

import com.google.devtools.intellij.IntellijAspectTestFixtureOuterClass.IntellijAspectTestFixture;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo.PyIdeInfo.PythonVersion;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo.TargetIdeInfo;
import com.google.idea.blaze.BazelIntellijAspectTest;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests py_binary */
@RunWith(JUnit4.class)
public class PyBinaryTest extends BazelIntellijAspectTest {
  @Test
  public void testPy3Binary() throws Exception {
    IntellijAspectTestFixture testFixture = loadTestFixture(":simple3_fixture");
    TargetIdeInfo target = findTarget(testFixture, ":simple3");
    assertThat(target.getKindString()).isEqualTo("py_binary");
    assertThat(relativePathsForArtifacts(target.getPyIdeInfo().getSourcesList()))
        .containsExactly(testRelative("simple.py"));
    assertThat(target.getPyIdeInfo().getPythonVersion()).isEqualTo(PythonVersion.PY3);

    assertThat(getOutputGroupFiles(testFixture, "intellij-info-py"))
        .containsExactly(testRelative(intellijInfoFileName("simple3")));
    assertThat(getOutputGroupFiles(testFixture, "intellij-info-generic")).isEmpty();
  }

  @Test
  public void testPyBinaryBuildfileArgs() throws Exception {
    IntellijAspectTestFixture testFixture = loadTestFixture(":buildfile_args_fixture");
    TargetIdeInfo target = findTarget(testFixture, ":buildfile_args");
    String compilationMode = System.getenv("COMPILATION_MODE_FOR_TEST");

    assertThat(compilationMode).isNotNull();

    assertThat(target.getKindString()).isEqualTo("py_binary");
    assertThat(target.getPyIdeInfo().getArgsList())
        .containsExactly("--ARG1", "--ARG2=" + compilationMode, "--ARG3='with spaces'");
  }

  @Test
  public void testExpandDataDeps() throws Exception {
    IntellijAspectTestFixture testFixture = loadTestFixture(":expand_datadeps_fixture");
    TargetIdeInfo target = findTarget(testFixture, ":expand_datadeps");

    assertThat(target.getKindString()).isEqualTo("py_binary");
    List<String> args = target.getPyIdeInfo().getArgsList();
    assertThat(args).hasSize(1);
    assertThat(args.get(0))
        .endsWith(
            "/aspect/testing/tests/src/com/google/idea/blaze/aspect/python/pybinary/datadepfile.txt");
  }
}
