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

import com.google.devtools.intellij.aspect.Common.ArtifactLocation;
import com.google.idea.blaze.aspect.IntellijAspectResource;
import java.util.Collections;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests the artifact representation
 */
@RunWith(JUnit4.class)
public class ArtifactTest {

  private static final String REPOSITORY_NAME = "clwb_virtual_includes_project";

  @Rule
  public final IntellijAspectResource aspect = new IntellijAspectResource(ArtifactTest.class, ":aspect_fixture");

  @Test
  public void testExternalFilePath() {
    final var ideInfo = aspect.findCIdeInfo("//main:main", REPOSITORY_NAME, Collections.emptyList());
    final var sourceFile = ideInfo.getRuleContext().getSources(0);

    assertThat(sourceFile.getRootPath()).isEqualTo("external/+_repo_rules2+clwb_virtual_includes_project");
    assertThat(sourceFile.getRelativePath()).isEqualTo("main/main.cc");
    assertThat(sourceFile.getIsExternal()).isTrue();
    assertThat(sourceFile.getIsSource()).isTrue();
  }

  @Test
  public void testSourceFilesAreCorrectlyMarkedAsSourceOrGenerated() {
    final var genIdeInfo = aspect.findCIdeInfo("//lib/strip_absolut:gen", REPOSITORY_NAME, Collections.emptyList());
    assertThat(genIdeInfo.getRuleContext().getHeadersList().get(0).getIsSource()).isFalse();

    final var srcIdeInfo = aspect.findCIdeInfo("//lib/strip_absolut:lib", REPOSITORY_NAME, Collections.emptyList());
    assertThat(srcIdeInfo.getRuleContext().getSourcesList().get(0).getIsSource()).isTrue();
    assertThat(srcIdeInfo.getRuleContext().getHeadersList().get(0).getIsSource()).isTrue();
  }

  @Test
  public void testCorrectPathToTargetBuildFile() {
    final var ideInfo = aspect.findTarget("//main:main", REPOSITORY_NAME, Collections.emptyList());
    final var buildFile = ideInfo.getBuildFileArtifactLocation();

    assertThat(buildFile.getRootPath()).isEqualTo("external/+_repo_rules2+clwb_virtual_includes_project");
    assertThat(buildFile.getRelativePath()).isEqualTo("main/BUILD");
    assertThat(buildFile.getIsExternal()).isTrue();
    assertThat(buildFile.getIsSource()).isTrue();
  }

  @Test
  public void testVirtualIncludesSymlinks() {
    final var ideInfo = aspect.findCIdeInfo("//main:main", REPOSITORY_NAME, Collections.emptyList());

    final var headers = ideInfo.getCompilationContext().getHeadersList();
    assertThat(headers).hasSize(11);

    final var virtualHeaders = headers.stream()
        .filter(it -> it.getRelativePath().contains("virtual_includes"))
        .toList();

    assertThat(virtualHeaders).hasSize(3);
    assertThat(virtualHeaders.stream().map(ArtifactLocation::getIsSource).distinct()).containsExactly(false);
  }
}
