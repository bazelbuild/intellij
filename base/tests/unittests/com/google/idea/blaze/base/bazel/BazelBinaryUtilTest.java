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
package com.google.idea.blaze.base.bazel;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.bazel.BazelBinaryUtil.Problem;
import com.intellij.util.system.OS;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link BazelBinaryUtil}. */
@RunWith(JUnit4.class)
public class BazelBinaryUtilTest {

  @Rule
  public final TemporaryFolder folder = new TemporaryFolder();

  private static final String NO_PATH = "";

  private File createExecutable(File directory, String name) throws IOException {
    final var file = new File(directory, name);
    assertThat(file.createNewFile()).isTrue();
    assumeTrue(file.setExecutable(true));
    return file;
  }

  @Test
  public void blankPathIsNotConfigured() {
    assertThat(BazelBinaryUtil.validate("", null, NO_PATH)).isEqualTo(Problem.NOT_CONFIGURED);
    assertThat(BazelBinaryUtil.validate("  ", null, NO_PATH)).isEqualTo(Problem.NOT_CONFIGURED);
  }

  @Test
  public void missingAbsolutePathDoesNotExist() {
    final var missing = new File(folder.getRoot(), "bazel").getAbsolutePath();
    assertThat(BazelBinaryUtil.validate(missing, null, NO_PATH)).isEqualTo(Problem.DOES_NOT_EXIST);
  }

  @Test
  public void directoryIsReported() throws IOException {
    final var directory = folder.newFolder("bazel").getAbsolutePath();
    assertThat(BazelBinaryUtil.validate(directory, null, NO_PATH)).isEqualTo(Problem.IS_A_DIRECTORY);
  }

  @Test
  public void executableAbsolutePathIsValid() throws IOException {
    final var binary = createExecutable(folder.getRoot(), "bazel");
    assertThat(BazelBinaryUtil.validate(binary.getAbsolutePath(), null, NO_PATH)).isNull();
  }

  @Test
  public void nonExecutableFileIsReported() throws IOException {
    // windows has no executable bit, File#canExecute is effectively an existence check there
    assumeFalse(OS.CURRENT == OS.Windows);

    final var binary = folder.newFile("bazel");
    assumeTrue(binary.setExecutable(false));

    assertThat(BazelBinaryUtil.validate(binary.getAbsolutePath(), null, NO_PATH)).isEqualTo(Problem.NOT_EXECUTABLE);
  }

  @Test
  public void danglingSymlinkDoesNotExist() throws IOException {
    assumeFalse(OS.CURRENT == OS.Windows);

    final var link = new File(folder.getRoot(), "bazel");
    Files.createSymbolicLink(link.toPath(), folder.getRoot().toPath().resolve("missing"));

    assertThat(BazelBinaryUtil.validate(link.getPath(), null, NO_PATH)).isEqualTo(Problem.DOES_NOT_EXIST);
  }

  @Test
  public void relativePathIsResolvedAgainstWorkspaceRoot() throws IOException {
    final var tools = folder.newFolder("tools");
    createExecutable(tools, "bazel");

    assertThat(BazelBinaryUtil.validate("tools/bazel", folder.getRoot().toPath(), NO_PATH)).isNull();
    assertThat(BazelBinaryUtil.validate("tools/missing", folder.getRoot().toPath(), NO_PATH)).isEqualTo(Problem.DOES_NOT_EXIST);
  }

  @Test
  public void bareNameIsLookedUpOnPath() throws IOException {
    final var binDir = folder.newFolder("bin");
    createExecutable(binDir, OS.CURRENT == OS.Windows ? "bazel.exe" : "bazel");

    assertThat(BazelBinaryUtil.validate("bazel", null, binDir.getAbsolutePath())).isNull();
  }

  @Test
  public void bareNameMissingFromPathIsReported() throws IOException {
    final var emptyDir = folder.newFolder("empty");

    assertThat(BazelBinaryUtil.validate("bazel", null, emptyDir.getAbsolutePath())).isEqualTo(Problem.DOES_NOT_EXIST);
    assertThat(BazelBinaryUtil.validate("bazel", null, NO_PATH)).isEqualTo(Problem.DOES_NOT_EXIST);
  }
}
