package com.google.idea.blaze.clwb;

import static com.google.idea.blaze.clwb.base.Assertions.assertContainsHeader;

import com.google.idea.blaze.clwb.base.ClwbHeadlessTestCase;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ExternalIncludesTest extends ClwbHeadlessTestCase {

  @Test
  public void testClwb() {
    final var errors = runSync(defaultSyncParams().build());
    errors.assertNoErrors();

    checkTest();
  }

  private void checkTest() {
    final var compilerSettings = findFileCompilerSettings("main/test.cc");

    assertContainsHeader("iostream", compilerSettings);
    assertContainsHeader("catch2/catch_test_macros.hpp", compilerSettings);
  }
}
