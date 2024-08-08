/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.buildmodifier;

import static com.google.common.truth.Truth.assertThat;

import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.TestModeFlags;
import com.intellij.util.system.CpuArch;
import com.intellij.util.system.OS;
import java.io.IOException;
import java.nio.file.Files;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link BuildifierDownloader}. */
@RunWith(JUnit4.class)
public class BuildifierDownloaderTest extends LightPlatformTestCase {

  @Override
  protected boolean runInDispatchThread() {
    return false;
  }

  @Test
  public void testMacArm64() throws IOException {
    doTest(OS.macOS, CpuArch.ARM64);
  }

  @Test
  public void testMacAmd64() throws IOException {
    doTest(OS.macOS, CpuArch.X86_64);
  }

  @Test
  public void testLinuxArm64() throws IOException {
    doTest(OS.Linux, CpuArch.ARM64);
  }

  @Test
  public void testLinuxAmd64() throws IOException {
    doTest(OS.Linux, CpuArch.X86_64);
  }

  @Test
  public void testWindowsAmd64() throws IOException {
    doTest(OS.Windows, CpuArch.X86_64);
  }

  private void doTest(OS os, CpuArch arch) throws IOException {
    TestModeFlags.set(BuildifierDownloader.OS_KEY, os);
    TestModeFlags.set(BuildifierDownloader.CPU_ARCH_KEY, arch);

    final var tempDir = Files.createTempDirectory(String.format("%s_%s", os, arch));
    TestModeFlags.set(BuildifierDownloader.DOWNLOAD_PATH_KEY, tempDir);

    assertThat(BuildifierDownloader.downloadSync()).isNotNull();
  }
}
