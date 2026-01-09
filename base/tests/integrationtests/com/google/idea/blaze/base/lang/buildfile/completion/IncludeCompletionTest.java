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
package com.google.idea.blaze.base.lang.buildfile.completion;

import static com.google.common.truth.Truth.assertThat;

import com.google.idea.blaze.base.lang.buildfile.BuildFileIntegrationTestCase;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.intellij.openapi.vfs.VirtualFile;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests auto-complete of *.MODULE.bazel files in 'include' statements. */
@RunWith(JUnit4.class)
public class IncludeCompletionTest extends BuildFileIntegrationTestCase {

  private VirtualFile createAndSetCaret(WorkspacePath workspacePath, String... fileContents) {
    VirtualFile file = workspace.createFile(workspacePath, fileContents);
    testFixture.configureFromExistingVirtualFile(file);
    return file;
  }

  @Test
  public void testLocalFileCompletion() {
    workspace.createFile(new WorkspacePath("deps.MODULE.bazel"));
    workspace.createFile(new WorkspacePath("BUILD"));
    VirtualFile file = createAndSetCaret(new WorkspacePath("MODULE.bazel"), "include(':<caret>'");

    assertThat(editorTest.completeIfUnique()).isTrue();
    assertFileContents(file, "include(':deps.MODULE.bazel'");
  }

  @Test
  public void testRootPackageCompletion() {
    workspace.createFile(new WorkspacePath("deps.MODULE.bazel"));
    workspace.createFile(new WorkspacePath("BUILD"));
    VirtualFile file = createAndSetCaret(new WorkspacePath("MODULE.bazel"), "include('//:<caret>'");

    assertThat(editorTest.completeIfUnique()).isTrue();
    assertFileContents(file, "include('//:deps.MODULE.bazel'");
  }

  @Test
  public void testNonLocalPackageCompletion() {
    workspace.createFile(new WorkspacePath("pkg/deps.MODULE.bazel"));
    workspace.createFile(new WorkspacePath("pkg/BUILD"));
    workspace.createFile(new WorkspacePath("BUILD"));
    VirtualFile file =
        createAndSetCaret(new WorkspacePath("MODULE.bazel"), "include('//pkg:<caret>'");

    assertThat(editorTest.completeIfUnique()).isTrue();
    assertFileContents(file, "include('//pkg:deps.MODULE.bazel'");
  }

  @Test
  public void testDirectoryCompletion() {
    workspace.createFile(new WorkspacePath("pkg/deps.MODULE.bazel"));
    workspace.createFile(new WorkspacePath("pkg/BUILD"));
    workspace.createFile(new WorkspacePath("BUILD"));
    VirtualFile file = createAndSetCaret(new WorkspacePath("MODULE.bazel"), "include('//<caret>'");

    assertThat(editorTest.completeIfUnique()).isTrue();
    assertFileContents(file, "include('//pkg'");
  }

  @Test
  public void testOnlyModuleBazelFilesShown() {
    workspace.createFile(new WorkspacePath("deps.MODULE.bazel"));
    workspace.createFile(new WorkspacePath("other.bzl"));
    workspace.createFile(new WorkspacePath("BUILD"));
    createAndSetCaret(new WorkspacePath("MODULE.bazel"), "include(':<caret>'");

    String[] completions = editorTest.getCompletionItemsAsStrings();
    assertThat(completions).hasLength(1);
    assertThat(completions[0]).contains("deps.MODULE.bazel");
  }

  @Test
  public void testModuleBazelItselfNotShown() {
    workspace.createFile(new WorkspacePath("deps.MODULE.bazel"));
    workspace.createFile(new WorkspacePath("BUILD"));
    createAndSetCaret(new WorkspacePath("MODULE.bazel"), "include(':<caret>'");

    String[] completions = editorTest.getCompletionItemsAsStrings();
    // Should only show deps.MODULE.bazel, not MODULE.bazel itself
    assertThat(completions).hasLength(1);
    assertThat(completions[0]).contains("deps.MODULE.bazel");
  }

  @Test
  public void testSelfNotInResults() {
    workspace.createFile(new WorkspacePath("other.MODULE.bazel"));
    workspace.createFile(new WorkspacePath("BUILD"));
    VirtualFile file =
        createAndSetCaret(new WorkspacePath("self.MODULE.bazel"), "include(':<caret>'");

    assertThat(editorTest.completeIfUnique()).isTrue();
    assertFileContents(file, "include(':other.MODULE.bazel'");
  }

  @Test
  public void testMultipleFiles() {
    workspace.createFile(new WorkspacePath("deps.MODULE.bazel"));
    workspace.createFile(new WorkspacePath("toolchains.MODULE.bazel"));
    workspace.createFile(new WorkspacePath("BUILD"));
    createAndSetCaret(new WorkspacePath("MODULE.bazel"), "include(':<caret>'");

    String[] completions = editorTest.getCompletionItemsAsStrings();
    assertThat(completions).hasLength(2);
    assertThat(completions)
        .asList()
        .containsExactly("':deps.MODULE.bazel'", "':toolchains.MODULE.bazel'");
  }
}
