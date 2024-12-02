package com.google.idea.blaze.clwb;

import static com.google.common.truth.Truth.assertThat;
import static com.google.idea.blaze.clwb.base.Assertions.assertContainsHeader;

import com.google.idea.blaze.clwb.base.BazelVersionRule;
import com.google.idea.blaze.clwb.base.ClwbIntegrationTestCase;
import com.google.idea.blaze.clwb.base.OSRule;
import com.intellij.util.system.OS;
import java.util.concurrent.ExecutionException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class QuerySyncTest extends ClwbIntegrationTestCase {

  // currently query sync only works on linux, TODO: fix mac and windows
  @Rule
  public final OSRule osRule = new OSRule(OS.Linux);

  // query sync requires bazel 6+
  @Rule
  public final BazelVersionRule bazelRule = new BazelVersionRule(6, 0);

  @Test
  public void testClwb() throws Exception {
    final var success = runQuerySync();
    assertThat(success).isTrue();

    checkAnalysis();
    checkCompiler();
  }

  private void checkAnalysis() throws ExecutionException {
    final var success = enableAnalysisFor(findProjectFile("main/hello-world.cc"));
    assertThat(success).isTrue();
  }

  private void checkCompiler() {
    final var compilerSettings = findFileCompilerSettings("main/hello-world.cc");

    // TODO: query sync selects the wrong compiler
    // if (SystemInfo.isMac) {
    //   assertThat(compilerSettings.getCompilerKind()).isEqualTo(ClangCompilerKind.INSTANCE);
    // } else if (SystemInfo.isLinux) {
    //   assertThat(compilerSettings.getCompilerKind()).isEqualTo(GCCCompilerKind.INSTANCE);
    // } else if (SystemInfo.isWindows) {
    //   assertThat(compilerSettings.getCompilerKind()).isEqualTo(MSVCCompilerKind.INSTANCE);
    // }

    assertContainsHeader("iostream", compilerSettings);
  }
}
