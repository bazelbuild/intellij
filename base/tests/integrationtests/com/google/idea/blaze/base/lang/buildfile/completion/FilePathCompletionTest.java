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
package com.google.idea.blaze.base.lang.buildfile.completion;

import static com.google.common.truth.Truth.assertThat;

import com.google.idea.blaze.base.lang.buildfile.BuildFileIntegrationTestCase;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.vfs.VirtualFile;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests file path code completion in BUILD file labels. */
@RunWith(JUnit4.class)
public class FilePathCompletionTest extends BuildFileIntegrationTestCase {

  @Test
  public void testUniqueDirectoryCompleted() {
    BuildFile file = createBuildFile(new WorkspacePath("java/BUILD"), "'//'");

    Editor editor = editorTest.openFileInEditor(file);
    editorTest.setCaretPosition(editor, 0, "'//".length());

    assertThat(editorTest.completeIfUnique()).isTrue();
    assertFileContents(file, "'//java'");
    // check caret remains inside closing quote
    editorTest.assertCaretPosition(editor, 0, "'//java".length());
  }

  @Test
  public void testInsertPairQuoteOptionRespected() {
    boolean old = CodeInsightSettings.getInstance().AUTOINSERT_PAIR_QUOTE;
    try {
      CodeInsightSettings.getInstance().AUTOINSERT_PAIR_QUOTE = false;
      BuildFile file = createBuildFile(new WorkspacePath("java/BUILD"), "'//");
      Editor editor = editorTest.openFileInEditor(file);
      editorTest.setCaretPosition(editor, 0, "'//".length());

      assertThat(editorTest.completeIfUnique()).isTrue();
      assertFileContents(file, "'//java");

      CodeInsightSettings.getInstance().AUTOINSERT_PAIR_QUOTE = true;
      file = createBuildFile(new WorkspacePath("foo/BUILD"), "'//j");
      editor = editorTest.openFileInEditor(file);
      editorTest.setCaretPosition(editor, 0, "'//j".length());

      assertThat(editorTest.completeIfUnique()).isTrue();
      assertFileContents(file, "'//java'");
    } finally {
      CodeInsightSettings.getInstance().AUTOINSERT_PAIR_QUOTE = old;
    }
  }

  @Test
  public void testUniqueMultiSegmentDirectoryCompleted() {
    BuildFile file = createBuildFile(new WorkspacePath("java/com/google/BUILD"), "'//'");

    Editor editor = editorTest.openFileInEditor(file);
    editorTest.setCaretPosition(editor, 0, "'//".length());

    assertThat(editorTest.completeIfUnique()).isTrue();
    assertFileContents(file, "'//java/com/google'");
  }

  // expected to be a typical workflow -- complete a segment,
  // get the possibilities, then start typing
  // next segment and complete again
  @Test
  public void testMultiStageCompletion() {
    workspace.createDirectory(new WorkspacePath("foo"));
    workspace.createDirectory(new WorkspacePath("bar"));
    workspace.createDirectory(new WorkspacePath("other"));
    workspace.createDirectory(new WorkspacePath("other/foo"));
    workspace.createDirectory(new WorkspacePath("other/bar"));

    BuildFile file = createBuildFile(new WorkspacePath("BUILD"), "'//'");

    Editor editor = editorTest.openFileInEditor(file);
    editorTest.setCaretPosition(editor, 0, "'//".length());

    String[] completionItems = editorTest.getCompletionItemsAsStrings();
    assertThat(completionItems).hasLength(3);

    editorTest.performTypingAction(editor, 'o');
    assertThat(editorTest.completeIfUnique()).isTrue();
    assertFileContents(file, "'//other'");
    editorTest.assertCaretPosition(editor, 0, "'//other".length());

    editorTest.performTypingAction(editor, '/');
    editorTest.performTypingAction(editor, 'f');
    assertThat(editorTest.completeIfUnique()).isTrue();
    assertFileContents(file, "'//other/foo'");
    editorTest.assertCaretPosition(editor, 0, "'//other/foo".length());
  }

  @Test
  public void testCompletionSuggestionString() {
    workspace.createDirectory(new WorkspacePath("foo"));
    workspace.createDirectory(new WorkspacePath("bar"));
    workspace.createDirectory(new WorkspacePath("other"));
    workspace.createDirectory(new WorkspacePath("ostrich/foo"));
    workspace.createDirectory(new WorkspacePath("ostrich/fooz"));

    VirtualFile file = createAndSetCaret(new WorkspacePath("BUILD"), "'//o<caret>'");

    String[] completionItems = editorTest.getCompletionItemsAsSuggestionStrings();
    assertThat(completionItems).asList().containsExactly("other", "ostrich");

    editorTest.performTypingAction(testFixture.getEditor(), 's');

    assertThat(editorTest.completeIfUnique()).isTrue();
    assertFileContents(file, "'//ostrich'");

    completionItems = editorTest.getCompletionItemsAsSuggestionStrings();
    assertThat(completionItems).asList().containsExactly("/foo", "/fooz");

    editorTest.performTypingAction(testFixture.getEditor(), '/');

    completionItems = editorTest.getCompletionItemsAsSuggestionStrings();
    assertThat(completionItems).asList().containsExactly("foo", "fooz");

    editorTest.performTypingAction(testFixture.getEditor(), 'f');

    completionItems = editorTest.getCompletionItemsAsSuggestionStrings();
    assertThat(completionItems).asList().containsExactly("foo", "fooz");
  }

  private VirtualFile createAndSetCaret(WorkspacePath workspacePath, String... fileContents) {
    VirtualFile file = workspace.createFile(workspacePath, fileContents);
    testFixture.configureFromExistingVirtualFile(file);
    return file;
  }
}
