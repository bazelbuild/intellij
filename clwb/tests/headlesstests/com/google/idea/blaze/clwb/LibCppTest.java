package com.google.idea.blaze.clwb;

import static com.google.common.truth.Truth.assertThat;

import com.google.idea.blaze.base.bazel.BazelVersion;
import com.google.idea.blaze.clwb.base.ClwbHeadlessTestCase;
import com.google.idea.testing.headless.OSRule;
import com.google.idea.testing.headless.ProjectViewBuilder;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.system.OS;
import com.jetbrains.cidr.lang.workspace.compiler.ClangCompilerKind;
import com.jetbrains.cidr.lang.workspace.headerRoots.HeadersSearchRoot;
import java.io.File;
import java.io.IOException;
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
    errors.assertNoIssues();

    checkCompiler();
    checkLibCpp();
  }

  @Override
  protected ProjectViewBuilder projectViewText(BazelVersion version) {
    return super.projectViewText(version).addBuildFlag(
        // set the compiler to clang, only required for linux
        "--action_env=CC=/usr/bin/clang",
        "--action_env=CXX=/usr/bin/clang",

        // use libc++ instead of libstdc++
        "--cxxopt=-stdlib=libc++",
        "--linkopt=-stdlib=libc++"
    );
  }

  private void checkCompiler() {
    final var compilerSettings = findFileCompilerSettings("main/hello-world.cc");
    assertThat(compilerSettings.getCompilerKind()).isEqualTo(ClangCompilerKind.INSTANCE);
  }

  private void checkLibCpp() throws IOException {
    final var compilerSettings = findFileCompilerSettings("main/hello-world.cc");
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
