/*
 * Copyright 2026 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.clwb;

import static com.google.common.truth.Truth.assertThat;
import static com.google.idea.blaze.clwb.base.Assertions.assertContainsCompilerFlag;

import com.google.idea.blaze.base.bazel.BazelVersion;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.clwb.base.ClwbHeadlessTestCase;
import com.google.idea.blaze.cpp.BlazeResolveConfigurationID;
import com.google.idea.testing.headless.BazelVersionRule;
import com.google.idea.testing.headless.OSRule;
import com.google.idea.testing.headless.ProjectViewBuilder;
import com.intellij.util.system.OS;
import com.jetbrains.cidr.lang.CLanguageKind;
import com.google.idea.blaze.cpp.BazelCompilerKind;
import com.google.idea.blaze.cpp.BazelGCCCompilerKind;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ArmToolchainTest extends ClwbHeadlessTestCase {

  // arm-none-eabi toolchain only supports linux
  @Rule
  public final OSRule osRule = new OSRule(OS.Linux);

  // Bazel 7 and 8 fail with an internal crash:
  // 1. the intellij_info_aspect now propagates through the binary attribute on platform_transition_binary
  // 2. The transition: the binary attribute with cfg = _transition_platform - a Starlark transition that rewrites //command_line_option:platforms to the ARM target platform
  // 3. The bug: Bazel 7 can't handle aspect propagation through attributes that have Starlark configuration transitions
  // 4. Building the same project directly with Bazel 7 (no aspects) succeeds, and the crash only occurs when the plugin applies its aspect during sync
  @Rule
  public final BazelVersionRule minBazelRule = BazelVersionRule.min(9, 0);

  @Override
  protected ProjectViewBuilder projectViewText(BazelVersion version) {
    return super.projectViewText(version)
        .setDeriveTargetsFromDirectories(false)
        .addTarget("//:main_u575");
  }

  @Test
  public void testClwb() {
    final var errors = runSync(defaultSyncParams().build());
    errors.assertNoErrors();

    checkResolveConfiguration();
    checkCompilerSettings();
  }

  /**
   * The platform_transition_binary transitions to a single platform, so main.c should have exactly one resolve
   * configuration.
   */
  private void checkResolveConfiguration() {
    final var configuration = findFileResolveConfiguration("srcs/main.c");

    final var ocConfigId = BlazeResolveConfigurationID.fromOCResolveConfiguration(configuration);
    assertThat(ocConfigId).isNotNull();

    // the target for //:main_u575 should not be present in the target map since it is not a cc_ target
    final var targetInfos = findTargetInfo(Label.create("//:main"));
    assertThat(targetInfos).hasSize(1);

    final var targetInfo = targetInfos.get(0);
    assertThat(targetInfo.getKey().configuration()).isEqualTo(ocConfigId.getConfigurationId());
  }

  /**
   * Verify that the resolve configuration contains ARM-specific compiler flags from the toolchain definition.
   */
  private void checkCompilerSettings() {
    final var compilerSettings = findFileCompilerSettings("srcs/main.c", CLanguageKind.C);

    assertThat(compilerSettings.getCompilerKind()).isInstanceOf(BazelCompilerKind.class);
    assertThat(compilerSettings.getCompilerKind()).isEqualTo(BazelGCCCompilerKind.INSTANCE);
    assertContainsCompilerFlag("-mcpu=cortex-m33", compilerSettings);
    assertContainsCompilerFlag("-mthumb", compilerSettings);
    assertContainsCompilerFlag("-DSTM32U575xx", compilerSettings);
  }
}
