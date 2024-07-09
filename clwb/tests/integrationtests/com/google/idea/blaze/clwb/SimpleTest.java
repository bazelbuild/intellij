package com.google.idea.blaze.clwb;

import com.google.idea.blaze.clwb.base.ClwbIntegrationTestCase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SimpleTest extends ClwbIntegrationTestCase {

  @Test
  public void testClwb() {
    final var errors = runSync(defaultSyncParams().build());
    errors.assertHasNoErrors();
  }
}
