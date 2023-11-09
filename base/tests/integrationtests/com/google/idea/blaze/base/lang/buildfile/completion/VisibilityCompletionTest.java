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
package com.google.idea.blaze.base.lang.buildfile.completion;

import static com.google.common.truth.Truth.assertThat;

import com.google.idea.blaze.base.lang.buildfile.BuildFileIntegrationTestCase;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.intellij.openapi.editor.Editor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class VisibilityCompletionTest extends BuildFileIntegrationTestCase {

  @Test
  public void testVisibilityReferenceWithDoubleQuote() throws Throwable {
    BuildFile file = createBuildFile(new WorkspacePath("BUILD"), "sh_binary(visibility = [\"/\"])");

    Editor editor = editorTest.openFileInEditor(file);
    editorTest.setCaretPosition(editor, 0, "sh_binary(visibility = [\"/".length());

    assertThat(editorTest.getCompletionItemsAsStrings())
        .asList()
        .containsExactly("\"//visibility:private", "\"//visibility:public");
  }

  @Test
  public void testVisibilityReferenceWithSimpleQuote() throws Throwable {
    BuildFile file = createBuildFile(new WorkspacePath("BUILD"), "sh_binary(visibility = ['/'])");

    Editor editor = editorTest.openFileInEditor(file);
    editorTest.setCaretPosition(editor, 0, "sh_binary(visibility = ['/".length());

    assertThat(editorTest.getCompletionItemsAsStrings())
        .asList()
        .containsExactly("'//visibility:private", "'//visibility:public");
  }

  @Test
  public void testVisibilityReferenceWithOtherPackages() throws Throwable {
    createBuildFile(new WorkspacePath("pkg/foo/BUILD"), "");
    BuildFile file = createBuildFile(new WorkspacePath("BUILD"), "sh_binary(visibility = ['/'])");

    Editor editor = editorTest.openFileInEditor(file);
    editorTest.setCaretPosition(editor, 0, "sh_binary(visibility = ['/".length());

    assertThat(editorTest.getCompletionItemsAsStrings())
        .asList()
        .containsExactly(
            "'//visibility:private",
            "'//visibility:public",
            "'//pkg/foo:__pkg__",
            "'//pkg/foo:__subpackages__");
  }

  @Test
  public void testVisibilityReferenceWithPackageGroup() throws Throwable {
    createBuildFile(new WorkspacePath("pkg/foo/BUILD"), "package_group(name = 'bob')");
    BuildFile file =
        createBuildFile(new WorkspacePath("BUILD"), "sh_binary(visibility = ['//pkg/foo:'])");

    Editor editor = editorTest.openFileInEditor(file);
    editorTest.setCaretPosition(editor, 0, "sh_binary(visibility = ['//pkg/foo:".length());

    assertThat(editorTest.getCompletionItemsAsStrings())
        .asList()
        .containsExactly(
                "'//pkg/foo:bob'",
                "'//pkg/foo:__pkg__",
                "'//pkg/foo:__subpackages__");
  }
}
