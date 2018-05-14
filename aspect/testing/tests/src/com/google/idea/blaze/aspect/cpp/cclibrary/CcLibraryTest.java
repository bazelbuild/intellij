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

import static com.google.common.truth.Truth.assertThat;
import static java.util.stream.Collectors.toList;

import com.google.common.collect.Iterables;
import com.google.devtools.intellij.IntellijAspectTestFixtureOuterClass.IntellijAspectTestFixture;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo.CIdeInfo;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo.CToolchainIdeInfo;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo.Dependency;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo.TargetIdeInfo;
import com.google.idea.blaze.BazelIntellijAspectTest;
import java.util.List;
import java.util.Objects;
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

    assertThat(relativePathsForArtifacts(target.getCIdeInfo().getSourceList()))
        .containsExactly(testRelative("simple/simple.cc"));
    assertThat(relativePathsForArtifacts(target.getCIdeInfo().getHeaderList()))
        .containsExactly(testRelative("simple/simple.h"));
    assertThat(relativePathsForArtifacts(target.getCIdeInfo().getTextualHeaderList()))
        .containsExactly(testRelative("simple/simple_textual.h"));

    CIdeInfo cTargetIdeInfo = target.getCIdeInfo();
    assertThat(cTargetIdeInfo.getTargetIncludeList()).containsExactly("foo/bar");
    assertThat(cTargetIdeInfo.getTargetCoptList())
        .containsExactly("-DGOPT", "-Ifoo/baz/", "-I", "other/headers");
    assertThat(cTargetIdeInfo.getTargetDefineList()).containsExactly("VERSION2");

    // Make sure our understanding of where this attributes show up in other providers is correct.
    List<String> transDefineList = cTargetIdeInfo.getTransitiveDefineList();
    assertThat(transDefineList).contains("VERSION2");

    List<String> transQuoteIncludeDirList = cTargetIdeInfo.getTransitiveQuoteIncludeDirectoryList();
    assertThat(transQuoteIncludeDirList).contains(".");

    // Can't test for this because the cc code stuffs source artifacts into
    // the output group
    // assertThat(testFixture.getIntellijResolveFilesList()).isEmpty();
  }

  @Test
  public void testCcLibraryHasToolchain() throws Exception {
    IntellijAspectTestFixture testFixture = loadTestFixture(":simple_fixture");
    List<TargetIdeInfo> toolchains =
        testFixture
            .getTargetsList()
            .stream()
            .filter(target -> target.getKindString().equals("cc_toolchain"))
            .collect(Collectors.toList());
    TargetIdeInfo toolchainTarget = Iterables.getOnlyElement(toolchains);
    assertThat(toolchainTarget.hasCToolchainIdeInfo()).isTrue();

    // Only cc_toolchain has toolchains
    TargetIdeInfo target = findTarget(testFixture, ":simple");
    assertThat(target.hasCToolchainIdeInfo()).isFalse();

    // Check that the library target deps on the toolchain.
    List<TargetIdeInfo> nativeToolchainDeps =
        dependenciesForTarget(target)
            .stream()
            .map(Dependency::getTarget)
            .map(targetKey -> findTarget(testFixture, targetKey.getLabel()))
            .filter(Objects::nonNull)
            .filter(TargetIdeInfo::hasCToolchainIdeInfo)
            .collect(toList());
    assertThat(toolchains).containsExactlyElementsIn(nativeToolchainDeps);

    CToolchainIdeInfo toolchainIdeInfo = toolchainTarget.getCToolchainIdeInfo();
    assertThat(toolchainIdeInfo.getCppExecutable()).isNotEmpty();
    // Should at least know the -std level (from some list of flags) to avoid b/70223102, unless the
    // default is sufficient.
    assertThat(
            toolchainIdeInfo
                .getCppOptionList()
                .stream()
                .anyMatch(option -> option.startsWith("-std=")))
        .isTrue();
    // There should be several include directories, including:
    // - from compiler (for xmmintrin.h, etc.) (gcc/.../include, or clang/.../include)
    // - libc (currently something without gcc or clang and ends with "include",
    //   which is a bit of a weak check)
    // - c++ library (usual several directories)
    //   - if libstdc++, something like .../x86_64-vendor-linux-gnu/include/c++/<version>
    //   - if libcxx, something like .../include/c++/<version>
    // This is assuming gcc or clang.
    assertThat(toolchainIdeInfo.getBuiltInIncludeDirectoryList()).isNotEmpty();
    assertThat(
            toolchainIdeInfo
                .getBuiltInIncludeDirectoryList()
                .stream()
                .anyMatch(
                    dir ->
                        (dir.contains("gcc/") || dir.contains("clang/"))
                            && dir.endsWith("include")))
        .isTrue();
    assertThat(
            toolchainIdeInfo
                .getBuiltInIncludeDirectoryList()
                .stream()
                .anyMatch(
                    dir ->
                        !dir.contains("gcc/")
                            && !dir.contains("clang/")
                            && dir.endsWith("include")))
        .isTrue();
    assertThat(
            toolchainIdeInfo
                .getBuiltInIncludeDirectoryList()
                .stream()
                .anyMatch(dir -> dir.contains("c++")))
        .isTrue();
    // Check that we have *some* options. Not everything is portable, so it's hard to be strict.
    assertThat(toolchainIdeInfo.getBaseCompilerOptionList()).isNotEmpty();
  }

  @Test
  public void testCcDependencies() throws Exception {
    IntellijAspectTestFixture testFixture = loadTestFixture(":dep_fixture");
    TargetIdeInfo lib2 = findTarget(testFixture, ":lib2");
    TargetIdeInfo lib1 = findTarget(testFixture, ":lib1");

    assertThat(lib1.hasCIdeInfo()).isTrue();
    assertThat(lib2.hasCIdeInfo()).isTrue();
    CIdeInfo cIdeInfo1 = lib1.getCIdeInfo();

    assertThat(cIdeInfo1.getTargetIncludeList()).containsExactly("foo/bar");
    assertThat(cIdeInfo1.getTargetCoptList()).containsExactly("-DGOPT", "-Ifoo/baz/");

    assertThat(cIdeInfo1.getTargetDefineList()).containsExactly("VERSION2");
    assertThat(cIdeInfo1.getTransitiveDefineList()).contains("VERSION2");
    assertThat(cIdeInfo1.getTransitiveDefineList()).contains("COMPLEX_IMPL");
  }
}
