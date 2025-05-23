package com.google.idea.blaze.clwb;

import static com.google.common.truth.Truth.assertThat;
import static com.google.idea.blaze.clwb.base.Assertions.assertContainsCompilerFlag;
import static com.google.idea.blaze.clwb.base.Assertions.assertContainsHeader;
import static com.google.idea.blaze.clwb.base.Assertions.assertContainsPattern;

import com.google.idea.blaze.base.lang.buildfile.psi.LoadStatement;
import com.google.idea.blaze.base.sync.autosync.ProjectTargetManager.SyncStatus;
import com.google.idea.blaze.clwb.base.ClwbHeadlessTestCase;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.cidr.lang.workspace.compiler.ClangCompilerKind;
import com.jetbrains.cidr.lang.workspace.compiler.GCCCompilerKind;
import com.jetbrains.cidr.lang.workspace.compiler.MSVCCompilerKind;
import java.io.IOException;
import java.nio.file.Files;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SimpleTest extends ClwbHeadlessTestCase {

  @Test
  public void testClwb() throws IOException {
    final var errors = runSync(defaultSyncParams().build());
    errors.assertNoErrors();

    checkCompiler();
    checkTest();
    checkXcode();
    checkResolveRulesCC();
    checkSyncStatus();
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
    assertContainsCompilerFlag("-Wall", compilerSettings);
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

    assertContainsPattern("export DEVELOPER_DIR=/.*/Xcode.*.app/Contents/Developer", scriptLines);
    assertContainsPattern("export SDKROOT=/.*/Xcode.*.app/Contents/Developer/.*", scriptLines);
  }

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

  private void checkSyncStatus() {
    assertThat(getSyncStatus("main/hello-world.cc")).isEqualTo(SyncStatus.SYNCED);
    assertThat(getSyncStatus("main/test.cc")).isEqualTo(SyncStatus.SYNCED);
  }

  // private void checkVFS() {
  //   final var data = BlazeProjectDataManager.getInstance(myProject).getBlazeProjectData();
  //   assertThat(data).isNotNull();

  //   final var rootFile = data.getBlazeInfo().getExecutionRoot();
  //   assertThat(rootFile).isNotNull();

  //   final var rootPath = rootFile.toPath();

  //   final var files = BazelDumpVFSAction.collect(rootPath)
  //       .stream()
  //       .map(rootPath::relativize);

  //   assertWithMessage(String.format("DEBUG WITH: '-Dfile.system.trace.loading=%s/<path>'", rootFile.getPath())).that(files).isEmpty();
  // }
}
