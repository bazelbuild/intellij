package com.google.idea.blaze.ijwb.base;

import com.google.idea.blaze.base.BazelHeavyIntegrationTest;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.projectRoots.ProjectJdkTable;

public abstract class IjwbIntegrationTestCase extends BazelHeavyIntegrationTest {

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
}
