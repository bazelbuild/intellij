package com.google.idea.blaze.ijwb.headless;

import com.google.idea.blaze.ijwb.headless.base.IjwbHeadlessTestCase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SimpleTest extends IjwbHeadlessTestCase {

  @Test
  public void testClwb() {
    final var errors = runSync(defaultSyncParams().build());
    errors.assertNoErrors();
  }
}
