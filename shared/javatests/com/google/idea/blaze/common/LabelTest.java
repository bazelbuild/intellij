/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.common;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.truth.Truth8;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class LabelTest {

  @Test
  public void testGetPackage_nonEmpty() {
    Truth8.assertThat(new Label("//package/path:rule").getPackage())
        .isEqualTo(Path.of("package/path"));
  }

  @Test
  public void testGetPackage_withWorkspace() {
    Truth8.assertThat(new Label("@myws//package/path:rule").getPackage())
        .isEqualTo(Path.of("package/path"));
  }

  @Test
  public void testGetName_simple() {
    Truth8.assertThat(new Label("//package/path:rule").getName()).isEqualTo(Path.of("rule"));
  }

  @Test
  public void testGetName_withWorkspace() {
    Truth8.assertThat(new Label("@someworkspace//package/path:rule").getName())
        .isEqualTo(Path.of("rule"));
  }

  @Test
  public void testGetPackage_empty() {
    Truth8.assertThat(new Label("//:rule").getPackage()).isEqualTo(Path.of(""));
  }

  @Test
  public void testGetPackage_empty_withWorkspace() {
    Truth8.assertThat(new Label("@workspace//:rule").getPackage()).isEqualTo(Path.of(""));
  }

  @Test
  public void testGetName_withDirectory() {
    Truth8.assertThat(new Label("//package/path:source/Class.java").getName())
        .isEqualTo(Path.of("source/Class.java"));
  }

  @Test
  public void testGetName_emptyPackage() {
    Truth8.assertThat(new Label("//:rule").getName()).isEqualTo(Path.of("rule"));
  }

  @Test
  public void testGetName_emptyPackage_withWorkspace() {
    Truth8.assertThat(new Label("@foo//:rule").getName()).isEqualTo(Path.of("rule"));
  }

  @Test
  public void testNew_badPackage() {
    assertThrows(IllegalArgumentException.class, () -> new Label("package/path:rule"));
  }

  @Test
  public void testNew_noName() {
    assertThrows(IllegalArgumentException.class, () -> new Label("//package/path"));
  }

  @Test
  public void testToFilePath() {
    Truth8.assertThat(new Label("//package/path:BUILD").toFilePath())
        .isEqualTo(Path.of("package/path/BUILD"));
  }

  @Test
  public void testGetWorkspace_empty() {
    assertThat(new Label("//package:rule").getWorkspaceName()).isEmpty();
  }

  @Test
  public void testGetWorkspace_nonEmpty() {
    assertThat(new Label("@myworkspace//package:rule").getWorkspaceName()).isEqualTo("myworkspace");
  }

  @Test
  public void testGetWorkspace_doubleAt() {
    assertThat(new Label("@@myws//package:rule").getWorkspaceName()).isEqualTo("myws");
  }

  @Test
  public void testNew_badWorkspace() {
    assertThrows(IllegalArgumentException.class, () -> new Label("@work:space//package/path"));
  }

  @Test
  public void doubleAtNormalization() {
    assertThat(new Label("@abc//:def")).isEqualTo(new Label("@@abc//:def"));
  }
}
