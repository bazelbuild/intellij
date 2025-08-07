package com.google.idea.blaze.clwb;

import static com.google.common.truth.Truth.assertThat;

import com.google.idea.blaze.base.sync.autosync.ProjectTargetManager.SyncStatus;
import com.google.idea.blaze.clwb.base.ClwbHeadlessTestCase;
import com.google.idea.testing.headless.BazelVersionRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ExampleTest extends ClwbHeadlessTestCase {

  // this test requires bazel 7+ because of googletest dependencies
  @Rule
  public final BazelVersionRule bazelRule = new BazelVersionRule(7, 0);

  @Test
  public void testClwb() {
    final var errors = runSync(defaultSyncParams().build());
    errors.assertNoIssues();

    assertThat(getSyncStatus("src/hello_world.cc")).isEqualTo(SyncStatus.SYNCED);
    assertThat(getSyncStatus("src/lib/greeting_lib.cc")).isEqualTo(SyncStatus.SYNCED);
  }
}
