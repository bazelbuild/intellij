/*
 * Copyright 2026 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.cpp;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.jetbrains.cidr.lang.workspace.compiler.ClangClCompilerKind;
import com.jetbrains.cidr.lang.workspace.compiler.ClangCompilerKind;
import com.jetbrains.cidr.lang.workspace.compiler.GCCCompilerKind;
import com.jetbrains.cidr.lang.workspace.compiler.MSVCCompilerKind;
import java.io.File;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Guards the split between the compiler kind reported to the IDE and the one used while probing
 * the compiler. See CPP-51220: a BazelCompilerKind reaching the workspace model makes Nova drop
 * all clang extensions.
 */
@RunWith(JUnit4.class)
public class BlazeCompilerSettingsTest {

  private static final String CLANG_VERSION = "clang version 19.1.0";
  private static final String GCC_VERSION = "gcc (GCC) 14.2.1 20240912";
  private static final String MSVC_VERSION = "Microsoft (R) C/C++ Optimizing Compiler Version 19";

  private static BlazeCompilerSettings settings(String name, String version) {
    return BlazeCompilerSettings.builder()
        .setCCompiler(new File("/usr/bin/" + name))
        .setCppCompiler(new File("/usr/bin/" + name))
        .setCSwitches(ImmutableList.of())
        .setCppSwitches(ImmutableList.of())
        .setVersion(version)
        .setName(name)
        .setEnvironment(ImmutableMap.of())
        .setBuiltInIncludes(ImmutableList.of())
        .setSysroot(null)
        .build();
  }

  @Test
  public void reportsStockKinds() {
    assertThat(settings("clang", CLANG_VERSION).getCompilerKind()).isEqualTo(ClangCompilerKind.INSTANCE);
    assertThat(settings("gcc", GCC_VERSION).getCompilerKind()).isEqualTo(GCCCompilerKind.INSTANCE);
    assertThat(settings("clang-cl", CLANG_VERSION).getCompilerKind()).isEqualTo(ClangClCompilerKind.INSTANCE);
    assertThat(settings("cl", MSVC_VERSION).getCompilerKind()).isEqualTo(MSVCCompilerKind.INSTANCE);
  }

  @Test
  public void neverReportsAWrapperKind() {
    assertThat(settings("clang", CLANG_VERSION).getCompilerKind()).isNotInstanceOf(BazelCompilerKind.class);
    assertThat(settings("gcc", GCC_VERSION).getCompilerKind()).isNotInstanceOf(BazelCompilerKind.class);
    assertThat(settings("clang-cl", CLANG_VERSION).getCompilerKind()).isNotInstanceOf(BazelCompilerKind.class);
    assertThat(settings("cl", MSVC_VERSION).getCompilerKind()).isNotInstanceOf(BazelCompilerKind.class);
  }

  @Test
  public void probesGccAndClangWithoutResponseFiles() {
    assertThat(settings("clang", CLANG_VERSION).getCompilerProbeKind()).isEqualTo(BazelClangCompilerKind.INSTANCE);
    assertThat(settings("gcc", GCC_VERSION).getCompilerProbeKind()).isEqualTo(BazelGCCCompilerKind.INSTANCE);
  }

  @Test
  public void probesMsvcWithTheStockKind() {
    assertThat(settings("clang-cl", CLANG_VERSION).getCompilerProbeKind()).isEqualTo(ClangClCompilerKind.INSTANCE);
    assertThat(settings("cl", MSVC_VERSION).getCompilerProbeKind()).isEqualTo(MSVCCompilerKind.INSTANCE);
  }
}
