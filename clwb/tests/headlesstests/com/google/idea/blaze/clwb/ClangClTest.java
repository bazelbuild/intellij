package com.google.idea.blaze.clwb;

import static com.google.common.truth.Truth.assertThat;
import static com.google.idea.blaze.clwb.base.Assertions.assertContainsHeader;
import static com.google.idea.blaze.clwb.base.Assertions.assertDefine;

import com.google.idea.blaze.clwb.base.ClwbHeadlessTestCase;
import com.google.idea.testing.headless.BazelVersionRule;
import com.google.idea.testing.headless.OSRule;
import com.intellij.util.system.OS;
import com.jetbrains.cidr.lang.workspace.compiler.ClangClCompilerKind;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ClangClTest extends ClwbHeadlessTestCase {

  @Test
  public void testClwb() {
    final var errors = runSync(defaultSyncParams().build());
    errors.assertNoErrors();

    checkCompiler();
  }

  private void checkCompiler() {
    final var compilerSettings = findFileCompilerSettings("main/main.cc");

    assertThat(compilerSettings.getCompilerKind()).isEqualTo(ClangClCompilerKind.INSTANCE);
    assertDefine("__llvm__", compilerSettings).isNotEmpty();

    assertContainsHeader("iostream", compilerSettings);
  }
}
