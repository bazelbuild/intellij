package com.google.idea.blaze.clwb;

import static com.google.common.truth.Truth.assertThat;
import static com.google.idea.blaze.clwb.base.Assertions.assertContainsHeader;
import static com.google.idea.blaze.clwb.base.Assertions.assertDefine;

import com.google.idea.blaze.clwb.base.BazelVersionRule;
import com.google.idea.blaze.clwb.base.ClwbIntegrationTestCase;
import com.google.idea.blaze.clwb.base.OSRule;
import com.intellij.util.system.OS;
import com.jetbrains.cidr.lang.workspace.compiler.ClangCompilerKind;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class LlvmToolchainTest extends ClwbIntegrationTestCase {

  // llvm toolchain currently does not support windows, otherwise this test should be fine to run on windows
  @Rule
  public final OSRule osRule = new OSRule(OS.Linux, OS.macOS);

  // llvm toolchain currently only supports bazel 7+
  @Rule
  public final BazelVersionRule bazelRule = new BazelVersionRule(7, 0);

  @Test
  public void testClwb() {
    final var errors = runSync(defaultSyncParams().build());
    errors.assertNoErrors();

    checkCompiler();
  }

  private void checkCompiler() {
    final var compilerSettings = findFileCompilerSettings("main/hello-world.cc");

    assertThat(compilerSettings.getCompilerKind()).isEqualTo(ClangCompilerKind.INSTANCE);
    assertDefine("__llvm__", compilerSettings).isNotEmpty();
    assertDefine("__VERSION__", compilerSettings).startsWith("\"Clang 19.1.0");

    assertContainsHeader("iostream", compilerSettings);
  }
}
