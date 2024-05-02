/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.aspect.cpp.cctoolchain;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.Lists;
import com.google.devtools.intellij.IntellijAspectTestFixtureOuterClass.IntellijAspectTestFixture;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo.CToolchainIdeInfo;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo.TargetIdeInfo;
import com.google.idea.blaze.BazelIntellijAspectTest;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests cc_toolchain and cc_toolchain_suite */
@RunWith(JUnit4.class)
public class CcToolchainTest extends BazelIntellijAspectTest {

  @Test
  public void testCcToolchain() throws Exception {
    IntellijAspectTestFixture testFixture = loadTestFixture(":fixture");
    List<TargetIdeInfo> toolchains = findToolchainTarget(testFixture);

    for (TargetIdeInfo toolchain : toolchains) {
      CToolchainIdeInfo toolchainInfo = toolchain.getCToolchainIdeInfo();
      assertThat(toolchainInfo.getBuiltInIncludeDirectoryList()).isNotEmpty();
      assertThat(toolchainInfo.getCCompiler()).isNotEmpty();
      assertThat(toolchainInfo.getCppCompiler()).isNotEmpty();
      assertThat(toolchainInfo.getTargetName()).isNotEmpty();
      assertThat(toolchainInfo.getCOptionList()).isNotEmpty();
      assertThat(toolchainInfo.getCppOptionList()).isNotEmpty();

      // Should at least know the -std level (from some list of flags) to avoid b/70223102.
      // This is *usually* deliberately chosen, though nowadays it may be fine to omit -std.
      // Compilers like Clang now default to a modern language level:
      // https://github.com/llvm-mirror/clang/commit/466d8da5f89b1a780f735c86f414fa69ce63221b
      // The -std= flag could also be in the form -Xclang-only=-std=<s>
      Pattern stdRegex = Pattern.compile("^(-std=.*|-X[a-zA-Z]+-only=-std=.*)");
      assertThat(
              toolchainInfo.getCppOptionList().stream()
                  .anyMatch(option -> stdRegex.matcher(option).matches()))
          .isTrue();
      // There should be several include directories, including:
      // - from compiler (for xmmintrin.h, etc.) (gcc/.../include, or clang/.../include)
      // - libc (currently something without gcc or clang and ends with "include",
      //   which is a bit of a weak check)
      // - c++ library (usual several directories)
      //   - if libstdc++, something like .../x86_64-vendor-linux-gnu/include/c++/<version>
      //   - if libcxx, something like .../include/c++/<version>
      // This is assuming gcc or clang.
      assertThat(
              toolchainInfo.getBuiltInIncludeDirectoryList().stream()
                  .anyMatch(
                      dir ->
                          (dir.contains("gcc/") || dir.contains("clang/"))
                              && dir.endsWith("include")))
          .isTrue();
      assertThat(
              toolchainInfo.getBuiltInIncludeDirectoryList().stream()
                  .anyMatch(
                      dir ->
                          !dir.contains("gcc/")
                              && !dir.contains("clang/")
                              && dir.endsWith("include")))
          .isTrue();
      assertThat(
              toolchainInfo.getBuiltInIncludeDirectoryList().stream()
                  .anyMatch(dir -> dir.contains("c++")))
          .isTrue();
    }
  }

  private static List<TargetIdeInfo> findToolchainTarget(IntellijAspectTestFixture testFixture) {
    List<TargetIdeInfo> result = Lists.newArrayList();
    for (TargetIdeInfo target : testFixture.getTargetsList()) {
      if (target.hasCToolchainIdeInfo()) {
        result.add(target);
      }
    }
    return result;
  }
}
