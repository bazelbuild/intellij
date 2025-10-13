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

import static com.google.common.truth.Truth.assertThat;

import com.google.devtools.intellij.ideinfo.IntellijIdeInfo.CIdeInfo;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo.TargetIdeInfo;
import com.google.idea.blaze.BazelIntellijAspectTest;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests the artifact representation */
@RunWith(JUnit4.class)
public class ArtifactTest extends BazelIntellijAspectTest {

  private static final String REPOSITORY_NAME = "clwb_virtual_includes_project";

  private TargetIdeInfo getIdeInfo(String label) throws IOException {
    final var testFixture = loadTestFixture(":aspect_fixture");
    final var target = findExternalTarget(testFixture, REPOSITORY_NAME, label);
    assertThat(target).isNotNull();

    return target;
  }

  private CIdeInfo getCIdeInfo(String label) throws IOException {
    final var target = getIdeInfo(label);
    assertThat(target.hasCIdeInfo()).isTrue();

    return target.getCIdeInfo();
  }

  @Test
  public void testExternalFilePath() throws Exception {
    final var ideInfo = getCIdeInfo("//main:main");
    final var sourceFile = ideInfo.getRuleContext().getSources(0);

    assertThat(sourceFile.getRootPath()).isEqualTo("external/+_repo_rules2+clwb_virtual_includes_project");
    assertThat(sourceFile.getRelativePath()).isEqualTo("main/main.cc");
    assertThat(sourceFile.getIsExternal()).isTrue();
    assertThat(sourceFile.getIsSource()).isTrue();

  }

  @Test
  public void testSourceFilesAreCorrectlyMarkedAsSourceOrGenerated() throws Exception {
    final var genIdeInfo = getCIdeInfo("//lib/strip_absolut:gen");
    assertThat(genIdeInfo.getRuleContext().getHeadersList().get(0).getIsSource()).isFalse();

    final var srcIdeInfo = getCIdeInfo("//lib/strip_absolut:lib");
    assertThat(srcIdeInfo.getRuleContext().getSourcesList().get(0).getIsSource()).isTrue();
    assertThat(srcIdeInfo.getRuleContext().getHeadersList().get(0).getIsSource()).isTrue();
  }

  @Test
  public void testCorrectPathToTargetBuildFile() throws Exception {
    final var ideInfo = getIdeInfo("//main:main");
    final var buildFile = ideInfo.getBuildFileArtifactLocation();

    assertThat(buildFile.getRootPath()).isEqualTo("external/+_repo_rules2+clwb_virtual_includes_project");
    assertThat(buildFile.getRelativePath()).isEqualTo("main/BUILD");
    assertThat(buildFile.getIsExternal()).isTrue();
    assertThat(buildFile.getIsSource()).isTrue();
  }
}
