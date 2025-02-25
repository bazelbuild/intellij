package com.google.idea.blaze.ijwb.headless;

import static com.google.common.truth.Truth.assertThat;
import static com.google.idea.blaze.ijwb.headless.base.Assertions.assertModuleContainsFile;

import com.google.idea.blaze.base.lang.buildfile.psi.LoadStatement;
import com.google.idea.blaze.ijwb.headless.base.IjwbHeadlessTestCase;
import com.intellij.psi.util.PsiTreeUtil;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SimpleTest extends IjwbHeadlessTestCase {

  @Test
  public void testIjwb() {
    final var errors = runSync(defaultSyncParams().build());
    errors.assertNoErrors();

    checkModule();
    checkResolveRulesJava();
  }

  private void checkModule() {
    final var file = findProjectFile("src/com/example/Simple.java");
    final var module = findWorkspaceModule();

    assertModuleContainsFile(module, file);
  }

  private void checkResolveRulesJava() {
    final var file = findProjectPsiFile("BUILD");

    final var load = PsiTreeUtil.findChildOfType(file, LoadStatement.class);
    assertThat(load).isNotNull();
    assertThat(load.getImportedPath()).isEqualTo("@rules_java//java:defs.bzl");

    for (final var symbol : load.getLoadedSymbols()) {
      final var reference = symbol.getReference();
      assertThat(reference).isNotNull();
      assertThat(reference.resolve()).isNotNull();
    }
  }
}
