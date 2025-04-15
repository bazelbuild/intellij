package com.google.idea.blaze.clwb;

import static com.google.common.truth.Truth.assertThat;

import com.google.idea.blaze.base.sync.autosync.ProjectTargetManager.SyncStatus;
import com.google.idea.blaze.clwb.base.ClwbHeadlessTestCase;
import com.intellij.openapi.util.SystemInfo;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class TargetCompatibleTest extends ClwbHeadlessTestCase {

  @Test
  public void testClwb() throws Exception {
    final var errors = runSync(defaultSyncParams().build());
    errors.assertNoErrors();

    checkTargets();
  }

  private void checkTargets() throws Exception {
    if (SystemInfo.isMac) {
      assertConfiguration("main/linux.cc", false);
      assertConfiguration("main/macos.cc", true);
      assertConfiguration("main/windows.cc", false);
    } else if (SystemInfo.isLinux) {
      assertConfiguration("main/linux.cc", true);
      assertConfiguration("main/macos.cc", false);
      assertConfiguration("main/windows.cc", false);
    } else if (SystemInfo.isWindows) {
      assertConfiguration("main/linux.cc", false);
      assertConfiguration("main/macos.cc", false);
      assertConfiguration("main/windows.cc", true);
    }
  }

  private void assertConfiguration(String relativePath, boolean configured) {
    final var configurations = getWorkspace().getConfigurationsForFile(findProjectFile(relativePath));

    if (configured) {
      assertThat(configurations).hasSize(1);
      assertThat(getSyncStatus(relativePath)).isEqualTo(SyncStatus.SYNCED);
    } else {
      assertThat(configurations).isEmpty();
      assertThat(getSyncStatus(relativePath)).isEqualTo(SyncStatus.UNSYNCED);
    }
  }
}
