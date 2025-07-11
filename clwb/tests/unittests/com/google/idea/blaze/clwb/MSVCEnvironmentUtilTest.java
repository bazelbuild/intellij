/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
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
import static org.junit.Assume.assumeTrue;

import com.google.idea.blaze.clwb.environment.MSVCEnvironmentUtil;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.testFramework.TestModeFlags;

import java.nio.file.Path;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link MSVCEnvironmentUtil}.
 */
@RunWith(JUnit4.class)
public class MSVCEnvironmentUtilTest {

  @Before
  public void windowsOnly() {
    assumeTrue(SystemInfo.isWindows);
  }

  private static void setBazelVC(String value) {
    TestModeFlags.set(MSVCEnvironmentUtil.BAZEL_VC_KEY, value);
  }

  @Test
  public void getToolSetPath_usesBazelVC() {
    setBazelVC("C:\\Program Files\\Microsoft Visual Studio\\2022\\Community\\VC");

    assertThat(MSVCEnvironmentUtil.getToolSetPath(Path.of("")))
        .isEqualTo("C:\\Program Files\\Microsoft Visual Studio\\2022\\Community");
    assertThat(MSVCEnvironmentUtil.getToolSetPath(null))
        .isEqualTo("C:\\Program Files\\Microsoft Visual Studio\\2022\\Community");
  }

  @Test
  public void getToolSetPath_emptyBazelVC() {
    setBazelVC("");

    assertThat(MSVCEnvironmentUtil.getToolSetPath(Path.of(""))).isNull();
    assertThat(MSVCEnvironmentUtil.getToolSetPath(null)).isNull();
  }

  @Test
  public void getToolSetPath_fromCompilerPath() {
    setBazelVC("");

    final var path = "C:\\Program Files\\Microsoft Visual Studio\\2022\\Community\\VC\\Tools\\MSVC\\14.40.33807\\bin\\Hostx64\\x64\\cl.exe";

    assertThat(MSVCEnvironmentUtil.getToolSetPath(Path.of(path)))
        .isEqualTo("c:\\program files\\microsoft visual studio\\2022\\community");
  }
}
