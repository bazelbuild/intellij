/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.async.process;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.base.async.process.ExternalTask.ExternalTaskImpl;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link ExternalTaskImpl}. */
@RunWith(JUnit4.class)
public final class ExternalTaskImplTest {

  @Test
  public void customizeEnvironmentPath_withoutCustomPath_shouldNotModifyMap() throws Exception {
    System.clearProperty(ExternalTaskImpl.CUSTOM_PATH_SYSTEM_PROPERTY);

    Map<String, String> envMap = new HashMap<>();
    ExternalTaskImpl.customizeEnvironmentPath(envMap);
    assertThat(envMap).isEmpty();

    Map<String, String> expected = ImmutableMap.of("USER", "grl");
    envMap = new HashMap<>(expected);
    ExternalTaskImpl.customizeEnvironmentPath(envMap);
    assertThat(envMap).isEqualTo(expected);

    expected = ImmutableMap.of("PATH", "/usr/local/bin:/usr/bin:/bin");
    envMap = new HashMap<>(expected);
    ExternalTaskImpl.customizeEnvironmentPath(envMap);
    assertThat(envMap).isEqualTo(expected);

    expected = ImmutableMap.of("PATH", "/usr/local/bin:/usr/bin:/bin", "LANG", "en_US.UTF-8");
    envMap = new HashMap<>(expected);
    ExternalTaskImpl.customizeEnvironmentPath(envMap);
    assertThat(envMap).isEqualTo(expected);
  }

  @Test
  public void customizeEnvironmentPath_withEmptyCustomPath_shouldNotSetPath() throws Exception {
    System.setProperty(ExternalTaskImpl.CUSTOM_PATH_SYSTEM_PROPERTY, "");
    Map<String, String> envMap = new HashMap<>();
    envMap.put("USER", "grl");

    ExternalTaskImpl.customizeEnvironmentPath(envMap);

    assertThat(envMap).hasSize(1);
    assertThat(envMap).containsEntry("USER", "grl");
    assertThat(envMap).doesNotContainKey("PATH");
  }

  @Test
  public void customizeEnvironmentPath_withEmptyCustomPath_shouldNotModifyPath() throws Exception {
    System.setProperty(ExternalTaskImpl.CUSTOM_PATH_SYSTEM_PROPERTY, "");
    Map<String, String> envMap = new HashMap<>();
    envMap.put("PATH", "/bin");

    ExternalTaskImpl.customizeEnvironmentPath(envMap);

    assertThat(envMap).containsEntry("PATH", "/bin");
  }

  @Test
  public void customizeEnvironmentPath_withCustomPath_shouldPrependPath() throws Exception {
    System.setProperty(ExternalTaskImpl.CUSTOM_PATH_SYSTEM_PROPERTY, "/custom/bin");
    Map<String, String> envMap = new HashMap<>();
    envMap.put("PATH", "/bin");

    ExternalTaskImpl.customizeEnvironmentPath(envMap);

    assertThat(envMap).containsEntry("PATH", "/custom/bin:/bin");
  }

  @Test
  public void customizeEnvironmentPath_withoutEnvPath_shouldSetPath() throws Exception {
    System.setProperty(ExternalTaskImpl.CUSTOM_PATH_SYSTEM_PROPERTY, "/custom/bin");
    Map<String, String> envMap = new HashMap<>();

    ExternalTaskImpl.customizeEnvironmentPath(envMap);

    assertThat(envMap).containsEntry("PATH", "/custom/bin");
  }

  @Test
  public void customizeEnvironmentPath_withEmptyEnvPath_shouldSetPath() throws Exception {
    System.setProperty(ExternalTaskImpl.CUSTOM_PATH_SYSTEM_PROPERTY, "/custom/bin");
    Map<String, String> envMap = new HashMap<>();
    envMap.put("PATH", "");

    ExternalTaskImpl.customizeEnvironmentPath(envMap);

    assertThat(envMap).containsEntry("PATH", "/custom/bin");
  }
}
