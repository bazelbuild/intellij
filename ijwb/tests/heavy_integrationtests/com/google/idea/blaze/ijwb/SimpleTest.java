package com.google.idea.blaze.ijwb;

import com.google.idea.blaze.ijwb.base.IjwbIntegrationTestCase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SimpleTest extends IjwbIntegrationTestCase {

  @Test
  public void testIjwb() {
    final var errors = runSync(defaultSyncParams().build());
    errors.assertNoErrors();

    checkConfiguration();
  }

  private void checkConfiguration() {
    final var javaFile = findProjectPsiFile("src/simple/Main.java");
    final var kotlinFile = findProjectPsiFile("src/simple/Main.kt");

    // not sure what to test here
  }
}
