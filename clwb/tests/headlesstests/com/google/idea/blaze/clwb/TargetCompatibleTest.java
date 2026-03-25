/*
 * Copyright 2026 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.idea.blaze.clwb;

import static com.google.common.truth.Truth.assertThat;

import com.google.idea.blaze.base.sync.autosync.ProjectTargetManager.SyncStatus;
import com.google.idea.blaze.clwb.base.ClwbHeadlessTestCase;
import com.google.idea.testing.headless.BazelVersionRule;
import com.intellij.openapi.util.SystemInfo;
import org.junit.Rule;
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
