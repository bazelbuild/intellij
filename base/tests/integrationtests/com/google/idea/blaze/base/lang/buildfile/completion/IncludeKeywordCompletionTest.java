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

/** Tests auto-complete of 'include' keyword in MODULE.bazel files. */
@RunWith(JUnit4.class)
public class IncludeKeywordCompletionTest extends BuildFileIntegrationTestCase {

  private VirtualFile createAndSetCaret(WorkspacePath workspacePath, String... fileContents) {
    VirtualFile file = workspace.createFile(workspacePath, fileContents);
    testFixture.configureFromExistingVirtualFile(file);
    return file;
  }

  @Test
  public void testIncludeKeywordCompletion() {
    VirtualFile file = createAndSetCaret(new WorkspacePath("MODULE.bazel"), "inclu<caret>");

    // When only "include" matches, it auto-completes (with parentheses added)
    assertThat(editorTest.completeIfUnique()).isTrue();
    assertFileContents(file, "include()");
  }

  @Test
  public void testIncludeKeywordInBuildFile() {
    // include should appear in completion even in BUILD files
    // (the annotator will mark it as error if used)
    VirtualFile file = createAndSetCaret(new WorkspacePath("BUILD"), "inclu<caret>");

    // When only "include" matches, it auto-completes (with parentheses added)
    assertThat(editorTest.completeIfUnique()).isTrue();
    assertFileContents(file, "include()");
  }
}
