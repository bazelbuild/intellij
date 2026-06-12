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
import static com.google.idea.testing.headless.Assertions.abort;

import com.google.idea.blaze.base.bazel.BazelVersion;
import com.google.idea.blaze.clwb.base.ClwbHeadlessTestCase;
import com.google.idea.testing.headless.BazelVersionRule;
import com.google.idea.testing.headless.OSRule;
import com.google.idea.testing.headless.ProjectViewBuilder;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.system.OS;
import com.google.idea.blaze.cpp.BazelClangCompilerKind;
import com.google.idea.blaze.cpp.BazelCompilerKind;
import com.jetbrains.cidr.lang.workspace.headerRoots.HeadersSearchRoot;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import javax.annotation.Nullable;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class LibCppTest extends ClwbHeadlessTestCase {

  // only the macOS and linux runners have llvm available
  @Rule
  public final OSRule osRule = new OSRule(OS.Linux, OS.macOS);

  @Test
  public void testClwb() throws IOException {
    assertExists(new File("/usr/bin/clang"));

    final var errors = runSync(defaultSyncParams().build());
    errors.assertNoErrors();

    checkCompiler();
    checkLibCpp();
  }

  @Override
  protected ProjectViewBuilder projectViewText(BazelVersion version) {
    final var builder = super.projectViewText(version);
    final var clang = resolveClang();

    // set the compiler to clang, only required for linux
    builder.addBuildFlag(
        "--repo_env=CC=" + clang,
        "--repo_env=CXX=" + clang,
        "--action_env=CC=" + clang,
        "--action_env=CXX=" + clang
    );

    return builder.addBuildFlag(
        // use libc++ instead of libstdc++
        "--cxxopt=-stdlib=libc++",
        "--linkopt=-stdlib=libc++"
    );
  }

  // /usr/bin/clang is usually a symlink into the LLVM installation and rules_cc
  // 0.2.19 stopped resolving this symlink; when clang is invoked through the
  // symlink together it cannot locate its own libc++ headers.
  private static String resolveClang() {
    try {
      return Path.of("/usr/bin/clang").toRealPath().toString();
    } catch (IOException e) {
      abort("could not resolve /usr/bin/clang", e);
      throw new AssertionError(e); // unreachable, abort always throws
    }
  }

  private void checkCompiler() {
    final var compilerSettings = findFileCompilerSettings("main/main.cc");
    assertThat(compilerSettings.getCompilerKind()).isInstanceOf(BazelCompilerKind.class);
    assertThat(compilerSettings.getCompilerKind()).isEqualTo(BazelClangCompilerKind.INSTANCE);
  }

  private void checkLibCpp() throws IOException {
    final var compilerSettings = findFileCompilerSettings("main/main.cc");
    final var roots = compilerSettings.getHeadersSearchRoots().getAllRoots();

    final var candidates = roots.stream()
        .map(this::resolveIostream)
        .filter(Objects::nonNull)
        .toList();
    assertThat(candidates).hasSize(1);

    final var text = VfsUtilCore.loadText(candidates.getFirst());
    assertThat(text).contains("// Part of the LLVM Project");
  }

  @Nullable
  private VirtualFile resolveIostream(HeadersSearchRoot root) {
    final var file = root.getVirtualFile();
    if (file == null) {
      return null;
    }

    return file.findFileByRelativePath("iostream");
  }
}
