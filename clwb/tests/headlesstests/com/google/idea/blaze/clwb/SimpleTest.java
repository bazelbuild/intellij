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
import static com.google.idea.blaze.clwb.base.Assertions.assertContainsCompilerFlag;
import static com.google.idea.blaze.clwb.base.Assertions.assertContainsHeader;
import static com.google.idea.blaze.clwb.base.Assertions.assertDefine;
import static com.google.idea.blaze.clwb.base.Assertions.assertNotContainsCompilerFlag;

import com.google.idea.blaze.base.lang.buildfile.psi.LoadStatement;
import com.google.idea.blaze.base.sync.autosync.ProjectTargetManager.SyncStatus;
import com.google.idea.blaze.clwb.base.ClwbHeadlessTestCase;
import com.google.idea.blaze.cpp.BazelClangCompilerKind;
import com.google.idea.blaze.cpp.BazelCompilerKind;
import com.google.idea.blaze.cpp.BazelGCCCompilerKind;
import com.google.idea.blaze.cpp.BlazeCWorkspace;
import com.google.idea.testing.headless.BazelVersionRule;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.cidr.lang.CLanguageKind;
import com.jetbrains.cidr.lang.OCLanguageKind;
import com.jetbrains.cidr.lang.workspace.compiler.MSVCCompilerKind;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SimpleTest extends ClwbHeadlessTestCase {

  @Test
  public void testClwb() {
    final var errors = runSync(defaultSyncParams().build());
    errors.assertNoErrors();

    checkCompiler();
    checkTest();
    checkXcode();
    checkResolveRulesCC();
    checkSyncStatus();
  }

  private void checkCompiler() {
    final var compilerSettingsCC = findFileCompilerSettings("main/main.cc", CLanguageKind.CPP);
    final var compilerSettingsC = findFileCompilerSettings("main/main.c", CLanguageKind.C);

    if (SystemInfo.isMac) {
      assertThat(compilerSettingsCC.getCompilerKind()).isInstanceOf(BazelCompilerKind.class);
      assertThat(compilerSettingsCC.getCompilerKind()).isEqualTo(BazelClangCompilerKind.INSTANCE);
      assertThat(compilerSettingsC.getCompilerKind()).isInstanceOf(BazelCompilerKind.class);
      assertThat(compilerSettingsC.getCompilerKind()).isEqualTo(BazelClangCompilerKind.INSTANCE);
    } else if (SystemInfo.isLinux) {
      assertThat(compilerSettingsCC.getCompilerKind()).isInstanceOf(BazelCompilerKind.class);
      assertThat(compilerSettingsCC.getCompilerKind()).isEqualTo(BazelGCCCompilerKind.INSTANCE);
      assertThat(compilerSettingsC.getCompilerKind()).isInstanceOf(BazelCompilerKind.class);
      assertThat(compilerSettingsC.getCompilerKind()).isEqualTo(BazelGCCCompilerKind.INSTANCE);
    } else if (SystemInfo.isWindows) {
      assertThat(compilerSettingsCC.getCompilerKind()).isEqualTo(MSVCCompilerKind.INSTANCE);
      assertThat(compilerSettingsC.getCompilerKind()).isEqualTo(MSVCCompilerKind.INSTANCE);
    }

    assertContainsHeader("iostream", compilerSettingsCC);

    assertContainsCompilerFlag("-Wall", compilerSettingsCC);
    assertContainsCompilerFlag("-DCXXOPTS", compilerSettingsCC);
    assertNotContainsCompilerFlag("-DCONLYOPTS", compilerSettingsCC);
    assertContainsCompilerFlag("-Wall", compilerSettingsC);
    assertContainsCompilerFlag("-DCONLYOPTS", compilerSettingsC);
    assertNotContainsCompilerFlag("-DCXXOPTS", compilerSettingsC);

    assertDefine("SIMPLE_DEFINE", compilerSettingsCC).isEqualTo("42");
    assertDefine("SPACE_DEFINE", compilerSettingsCC).isEqualTo("1 2 3");
  }

  private void checkTest() {
    final var compilerSettings = findFileCompilerSettings("main/test.cc");

    assertContainsHeader("iostream", compilerSettings);
    assertContainsHeader("catch2/catch_test_macros.hpp", compilerSettings);
  }

  private void checkXcode() {
    if (!SystemInfo.isMac) {
      return;
    }


    final var configs = BlazeCWorkspace.getInstance(myProject).getResolveConfigurations();
    assertThat(configs).isNotEmpty();

    final var environment = configs.get(0).getConfigurationData().compilerSettings().environment();
    assertThat(environment).containsKey("DEVELOPER_DIR");
    assertThat(environment.get("DEVELOPER_DIR")).containsMatch("Xcode.*\\.app/Contents/Developer");
    assertThat(environment).containsKey("SDKROOT");
    assertThat(environment.get("SDKROOT")).containsMatch("Xcode.*\\.app/Contents/Developer");
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
    assertThat(getSyncStatus("main/main.cc")).isEqualTo(SyncStatus.SYNCED);
    assertThat(getSyncStatus("main/main.c")).isEqualTo(SyncStatus.SYNCED);
    assertThat(getSyncStatus("main/test.cc")).isEqualTo(SyncStatus.SYNCED);
  }
}
