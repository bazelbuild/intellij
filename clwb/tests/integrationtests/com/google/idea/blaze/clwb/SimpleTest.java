package com.google.idea.blaze.clwb;

import static com.google.common.truth.Truth.assertThat;
import static com.google.idea.blaze.clwb.base.Assertions.assertContainsHeader;
import static com.google.idea.blaze.clwb.base.Assertions.assertProjectStatus;

import com.intellij.openapi.util.SystemInfo;
import com.jetbrains.cidr.lang.workspace.compiler.ClangCompilerKind;
import com.jetbrains.cidr.lang.workspace.compiler.GCCCompilerKind;
import com.jetbrains.cidr.lang.workspace.compiler.MSVCCompilerKind;
import com.google.idea.blaze.clwb.base.ClwbIntegrationTestCase;
import com.jetbrains.cidr.project.ui.widget.Status;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SimpleTest extends ClwbIntegrationTestCase {

  @Test
  public void testClwb() {
    final var errors = runSync(defaultSyncParams().build());
    errors.assertNoErrors();

    checkCompiler();
    checkTest();
    checkStatus();
  }

  private void checkCompiler() {
    final var compilerSettings = findFileCompilerSettings("main/hello-world.cc");

    if (SystemInfo.isMac) {
      assertThat(compilerSettings.getCompilerKind()).isEqualTo(ClangCompilerKind.INSTANCE);
    } else if (SystemInfo.isLinux) {
      assertThat(compilerSettings.getCompilerKind()).isEqualTo(GCCCompilerKind.INSTANCE);
    } else if (SystemInfo.isWindows) {
      assertThat(compilerSettings.getCompilerKind()).isEqualTo(MSVCCompilerKind.INSTANCE);
    }

    assertContainsHeader("iostream", compilerSettings);
  }

  private void checkTest() {
    final var compilerSettings = findFileCompilerSettings("main/test.cc");

    assertContainsHeader("iostream", compilerSettings);
    assertContainsHeader("catch2/catch_test_macros.hpp", compilerSettings);
  }

  private void checkStatus() {
    final var file = findProjectFile("main/hello-world.cc");

    assertProjectStatus(getProject(), file, Status.OK);
  }
}
