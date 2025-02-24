package com.google.idea.blaze.ijwb.headless.base;

import com.google.idea.testing.headless.HeadlessTestCase;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.projectRoots.ProjectJdkTable;

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
}
