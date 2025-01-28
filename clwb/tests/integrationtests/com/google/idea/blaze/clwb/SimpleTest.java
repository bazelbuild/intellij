package com.google.idea.blaze.clwb;

import static com.google.common.truth.Truth.assertThat;
import static com.google.idea.blaze.clwb.base.Assertions.assertContainsHeader;
import static com.google.idea.blaze.clwb.base.Assertions.assertContainsPattern;

import com.intellij.openapi.util.SystemInfo;
import com.jetbrains.cidr.lang.workspace.compiler.ClangCompilerKind;
import com.jetbrains.cidr.lang.workspace.compiler.GCCCompilerKind;
import com.jetbrains.cidr.lang.workspace.compiler.MSVCCompilerKind;
import com.google.idea.blaze.clwb.base.ClwbIntegrationTestCase;
import java.io.IOException;
import java.nio.file.Files;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SimpleTest extends ClwbIntegrationTestCase {

  @Test
  public void testClwb() throws IOException {
    final var errors = runSync(defaultSyncParams().build());
    errors.assertNoErrors();

    checkCompiler();
    checkTest();
    checkXcode();
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

  private void checkXcode() throws IOException {
    if (!SystemInfo.isMac) {
      return;
    }

    final var compilerSettings = findFileCompilerSettings("main/test.cc");

    final var compilerExecutable = compilerSettings.getCompilerExecutable();
    assertThat(compilerExecutable).isNotNull();

    final var scriptLines = Files.readAllLines(compilerSettings.getCompilerExecutable().toPath());

    assertContainsPattern("export DEVELOPER_DIR=/.*/Xcode.app/Contents/Developer", scriptLines);
    assertContainsPattern("export SDKROOT=/.*/Xcode.app/Contents/Developer/.*", scriptLines);
  }
}
