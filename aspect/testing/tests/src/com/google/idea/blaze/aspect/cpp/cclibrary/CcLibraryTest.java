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
package com.google.idea.blaze.aspect.cpp.cclibrary;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;

import com.google.devtools.intellij.IntellijAspectTestFixtureOuterClass.IntellijAspectTestFixture;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo.CIdeInfo;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo.TargetIdeInfo;
import com.google.idea.blaze.BazelIntellijAspectTest;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests cc_library */
@RunWith(JUnit4.class)
public class CcLibraryTest extends BazelIntellijAspectTest {
  @Test
  public void testCcLibrary() throws Exception {
    IntellijAspectTestFixture testFixture = loadTestFixture(":simple_fixture");
    TargetIdeInfo target = findTarget(testFixture, ":simple");

    assertThat(target.getKindString()).isEqualTo("cc_library");
    assertThat(target.hasCIdeInfo()).isTrue();
    assertThat(target.hasJavaIdeInfo()).isFalse();
    assertThat(target.hasAndroidIdeInfo()).isFalse();
    assertThat(target.hasPyIdeInfo()).isFalse();

    final var ruleContext = target.getCIdeInfo().getRuleContext();
    final var compilationContext = target.getCIdeInfo().getCompilationContext();

    assertThat(relativePathsForArtifacts(ruleContext.getSourcesList()))
        .containsExactly(testRelative("simple/simple.cc"));
    assertThat(relativePathsForArtifacts(ruleContext.getHeadersList()))
        .containsExactly(testRelative("simple/simple.h"));
    assertThat(relativePathsForArtifacts(ruleContext.getTextualHeadersList()))
        .containsExactly(testRelative("simple/simple_textual.h"));

    assertThat(ruleContext.getCoptsList()).containsExactly("-DGOPT", "-Ifoo/baz/", "-I", "other/headers");

    // Make sure our understanding of where this attributes show up in other providers is correct.
    assertThat(compilationContext.getSystemIncludesList()).contains(testRelative("foo/bar"));
    assertThat(compilationContext.getDefinesList()).contains("VERSION2");

    assertThat(compilationContext.getQuoteIncludesList()).contains(".");

    // Can't test for this because the cc code stuffs source artifacts into
    // the output group
    // assertThat(testFixture.getIntellijResolveFilesList()).isEmpty();
  }

  @Test
  public void testCcLibraryHasToolchain() throws Exception {
    IntellijAspectTestFixture testFixture = loadTestFixture(":simple_fixture");
    List<TargetIdeInfo> toolchains =
        testFixture.getTargetsList().stream()
            .filter(x -> x.hasCToolchainIdeInfo() && x.getKindString().equals("cc_toolchain_alias"))
            .collect(Collectors.toList());
    // TODO(b/200011173): Remove once Blaze/Bazel has been released with Starlark cc_library.
    if (toolchains.isEmpty()) {
      toolchains =
          testFixture.getTargetsList().stream()
              .filter(TargetIdeInfo::hasCToolchainIdeInfo)
              .collect(toImmutableList());
    }
    assertThat(toolchains).hasSize(1);

    TargetIdeInfo target = findTarget(testFixture, ":simple");
    assertThat(dependenciesForTarget(target)).contains(dep(toolchains.get(0)));
  }

  @Test
  public void testCcDependencies() throws Exception {
    IntellijAspectTestFixture testFixture = loadTestFixture(":dep_fixture");
    TargetIdeInfo lib2 = findTarget(testFixture, ":lib2");
    TargetIdeInfo lib1 = findTarget(testFixture, ":lib1");

    assertThat(lib1.hasCIdeInfo()).isTrue();
    assertThat(lib2.hasCIdeInfo()).isTrue();
    final var ruleContext = lib1.getCIdeInfo().getRuleContext();
    final var compilationContext = lib1.getCIdeInfo().getCompilationContext();

    assertThat(compilationContext.getSystemIncludesList()).contains(testRelative("foo/bar"));
    assertThat(compilationContext.getSystemIncludesList()).contains(testRelative("baz/lib"));

    assertThat(ruleContext.getCoptsList()).containsExactly("-DGOPT", "-Ifoo/baz/");

    assertThat(compilationContext.getDefinesList()).contains("VERSION2");
    assertThat(compilationContext.getDefinesList()).contains("COMPLEX_IMPL");
  }
}
