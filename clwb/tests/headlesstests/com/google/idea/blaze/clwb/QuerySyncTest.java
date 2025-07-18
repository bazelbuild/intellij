package com.google.idea.blaze.clwb;

import static com.google.common.truth.Truth.assertThat;
import static com.google.idea.blaze.clwb.base.Assertions.assertContainsCompilerFlag;
import static com.google.idea.blaze.clwb.base.Assertions.assertContainsHeader;

import com.google.idea.blaze.base.bazel.BazelVersion;
import com.google.idea.blaze.base.lang.buildfile.psi.LoadStatement;
import com.google.idea.blaze.clwb.base.AllowedVfsRoot;
import com.google.idea.blaze.clwb.base.ClwbHeadlessTestCase;
import com.google.idea.testing.headless.BazelVersionRule;
import com.google.idea.testing.headless.OSRule;
import com.google.idea.testing.headless.ProjectViewBuilder;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.system.OS;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class QuerySyncTest extends ClwbHeadlessTestCase {

  // currently query sync only works on linux, TODO: fix mac and windows
  @Rule
  public final OSRule osRule = new OSRule(OS.Linux);

  @Override
  protected ProjectViewBuilder projectViewText(BazelVersion version) {
    return super.projectViewText(version).useQuerySync(true);
  }

  @Override
  protected void addAllowedVfsRoots(ArrayList<AllowedVfsRoot> roots) {
    super.addAllowedVfsRoots(roots);
    roots.add(AllowedVfsRoot.recursive("external/catch2+/src"));
  }

  @Test
  public void testClwb() throws Exception {
    final var success = runQuerySync();
    assertThat(success).isTrue();

    checkAnalysis();
    checkCompiler();
    checkTest();
    checkResolveRulesCC();
  }

  private void checkAnalysis() throws ExecutionException {
    final var result = enableAnalysisFor(findProjectFile("main/hello-world.cc"));
    result.assertNoErrors();
  }

  private void checkCompiler() {
    final var compilerSettings = findFileCompilerSettings("main/hello-world.cc");

    // TODO: query sync always uses clang : https://github.com/bazelbuild/intellij/issues/7177
    // if (SystemInfo.isMac) {
    //   assertThat(compilerSettings.getCompilerKind()).isEqualTo(ClangCompilerKind.INSTANCE);
    // } else if (SystemInfo.isLinux) {
    //   assertThat(compilerSettings.getCompilerKind()).isEqualTo(GCCCompilerKind.INSTANCE);
    // } else if (SystemInfo.isWindows) {
    //   assertThat(compilerSettings.getCompilerKind()).isEqualTo(MSVCCompilerKind.INSTANCE);
    // }

    assertContainsHeader("iostream", compilerSettings);
    assertContainsCompilerFlag("-Wall", compilerSettings);
  }

  private void checkTest() throws ExecutionException {
    final var result = enableAnalysisFor(findProjectFile("main/test.cc"));
    result.assertNoErrors();

    final var compilerSettings = findFileCompilerSettings("main/test.cc");

    assertContainsHeader("iostream", compilerSettings);
    assertContainsHeader("catch2/catch_test_macros.hpp", compilerSettings);
  }

  // TODO: find a common place for shared test between async (SimpleTest) and qsync
  private void checkResolveRulesCC() {
    final var file = findProjectPsiFile("main/BUILD");

    final var load = PsiTreeUtil.findChildOfType(file, LoadStatement.class);
    assertThat(load).isNotNull();
    assertThat(load.getImportedPath()).isEqualTo("@rules_cc//cc:defs.bzl");

    for (final var symbol : load.getLoadedSymbols()) {
      final var reference = symbol.getReference();
      assertThat(reference).isNotNull();
      assertThat(reference.resolve()).isNotNull();
    }
  }
}
