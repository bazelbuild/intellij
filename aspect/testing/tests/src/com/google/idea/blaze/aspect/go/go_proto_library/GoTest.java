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
package com.google.idea.blaze.aspect.go.go_proto_library;

import static com.google.common.truth.Truth.assertThat;

import com.google.devtools.intellij.IntellijAspectTestFixtureOuterClass.IntellijAspectTestFixture;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo.TargetIdeInfo;
import com.google.idea.blaze.BazelIntellijAspectTest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


/** Tests go_proto_library */
@RunWith(JUnit4.class)
public class GoTest extends BazelIntellijAspectTest {
  @Test
  public void testGoTest() throws Exception {
    IntellijAspectTestFixture testFixture = loadTestFixture(":simple_fixture");
    TargetIdeInfo target = findTarget(testFixture, ":proto");
    assertThat(target.getKindString()).isEqualTo("go_library");
    assertThat(relativePathsForArtifacts(target.getGoIdeInfo().getSourcesList()))
        .contains(testRelative("translators.go"));
    assertThat(relativePathsForArtifacts(target.getGoIdeInfo().getSourcesList()))
        .contains(testRelative("fooproto_go_proto_/github.com/bazelbuild/intellij/examples/go/with_proto/proto/fooserver.pb.go"));
  }
}
