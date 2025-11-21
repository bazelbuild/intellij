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
    final var testFixture = loadTestFixture(":simple_fixture");
    final var target = findTarget(testFixture, ":simple");
    assertThat(target.getKindString()).isEqualTo("cc_library");

    final var ideInfo = target.getCIdeInfo();
    assertThat(ideInfo.hasCompilationContext()).isTrue();
    assertThat(ideInfo.hasRuleContext()).isTrue();

    // rule context
    final var ruleCtx = target.getCIdeInfo().getRuleContext();
    assertThat(relativePathsForArtifacts(ruleCtx.getSourcesList()))
        .containsExactly(testRelative("simple/simple.cc"));
    assertThat(relativePathsForArtifacts(ruleCtx.getHeadersList()))
        .containsExactly(testRelative("simple/simple.h"));
    assertThat(relativePathsForArtifacts(ruleCtx.getTextualHeadersList()))
        .containsExactly(testRelative("simple/simple_textual.h"));
    assertThat(ruleCtx.getCoptsList())
        .containsExactly("-DGOPT", "-Ifoo/baz/", "-I", "other/headers");

    // compilation context
    final var compilationCtx = target.getCIdeInfo().getCompilationContext();
    assertThat(compilationCtx.getDefinesList()).containsExactly("VERSION2");
    assertThat(compilationCtx.getIncludesList()).contains(testRelative("foo/bar"));
    assertThat(compilationCtx.getQuoteIncludesList()).contains(".");
    assertThat(relativePathsForArtifacts(compilationCtx.getHeadersList()))
        .containsExactly(testRelative("simple/simple.h"), testRelative("simple/simple_textual.h"));

    // Can't test for this because the cc code stuffs source artifacts into
    // the output group
    // assertThat(testFixture.getIntellijResolveFilesList()).isEmpty();
  }

  @Test
  public void testCcLibraryHasToolchain() throws Exception {
    final var testFixture = loadTestFixture(":simple_fixture");
    final var toolchains = testFixture.getTargetsList().stream()
        .filter(x -> x.hasCToolchainIdeInfo() && x.getKindString().equals("cc_toolchain"))
        .collect(Collectors.toList());

    assertThat(toolchains).hasSize(1);

    final var target = findTarget(testFixture, ":simple");
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

    assertThat(compilationContext.getIncludesList()).contains(testRelative("foo/bar"));
    assertThat(compilationContext.getIncludesList()).contains(testRelative("baz/lib"));

    assertThat(ruleContext.getCoptsList()).containsExactly("-DGOPT", "-Ifoo/baz/");

    assertThat(compilationContext.getDefinesList()).contains("VERSION2");
    assertThat(compilationContext.getDefinesList()).contains("COMPLEX_IMPL");
  }
}
