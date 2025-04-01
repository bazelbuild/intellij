package com.google.idea.blaze.ijwb.headless.base;

import static com.google.common.truth.Truth.assertThat;

import com.google.idea.blaze.base.bazel.BazelVersion;
import com.google.idea.testing.headless.HeadlessTestCase;
import com.google.idea.testing.headless.ProjectViewBuilder;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import java.util.Arrays;

public class IjwbHeadlessTestCase extends HeadlessTestCase {

  @Override
  protected void tearDown() throws Exception {
    final var jdkTable = ProjectJdkTable.getInstance();

    WriteAction.runAndWait(() -> {
      for (final var jdk : jdkTable.getAllJdks()) {
        jdkTable.removeJdk(jdk);
      }
    });

    super.tearDown();
  }

  @Override
  protected ProjectViewBuilder projectViewText(BazelVersion version) {
    return super.projectViewText(version).addBuildFlag("--java_language_version=17");
  }

  protected Module findWorkspaceModule() {
    final var workspace = Arrays.stream(ModuleManager.getInstance(getProject()).getModules())
        .filter(module -> module.getName().equals(".workspace"))
        .findFirst();

    assertThat(workspace).isPresent();

    return workspace.get();
  }
}
