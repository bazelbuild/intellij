/*
 * Copyright 2026 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.idea.blaze.clwb;

import static com.google.common.truth.Truth.assertThat;
import static com.google.idea.blaze.clwb.base.Assertions.assertContainsHeader;
import static com.google.idea.blaze.clwb.base.Assertions.assertDefine;
import static com.google.idea.blaze.clwb.base.Utils.lookupCompilerSwitch;

import com.google.idea.blaze.base.bazel.BazelVersion;
import com.google.idea.blaze.clwb.base.ClwbHeadlessTestCase;
import com.google.idea.testing.headless.BazelVersionRule;
import com.google.idea.testing.headless.ProjectViewBuilder;
import com.google.idea.testing.headless.OSRule;
import com.intellij.util.system.OS;
import com.google.idea.blaze.cpp.BazelClangCompilerKind;
import com.google.idea.blaze.cpp.BazelCompilerKind;
import java.io.File;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class LlvmToolchainTest extends ClwbHeadlessTestCase {

  // llvm toolchain currently does not support windows, otherwise this test should be fine to run on windows
  @Rule
  public final OSRule osRule = new OSRule(OS.Linux, OS.macOS);

  @Override
  protected ProjectViewBuilder projectViewText(BazelVersion version) {
    // required because this test targets wasm
    return super.projectViewText(version).addBuildFlag("--platforms=@toolchains_llvm//platforms:wasm32");
  }

  @Test
  public void testClwb() {
    final var errors = runSync(defaultSyncParams().build());
    errors.assertNoErrors();

    checkCompiler();
  }

  private void checkCompiler() {
    final var compilerSettings = findFileCompilerSettings("main/hello-world.cc");

    assertThat(compilerSettings.getCompilerKind()).isInstanceOf(BazelCompilerKind.class);
    assertThat(compilerSettings.getCompilerKind()).isEqualTo(BazelClangCompilerKind.INSTANCE);
    assertDefine("__llvm__", compilerSettings).isNotEmpty();
    assertDefine("__VERSION__", compilerSettings).startsWith("\"Clang 19.1.0");

    final var sysroot = lookupCompilerSwitch("sysroot", compilerSettings).getFirst();
    assertExists(new File(sysroot));

    assertContainsHeader("stdlib.h", compilerSettings);
    assertContainsHeader("wasi/wasip2.h", compilerSettings);
  }
}
