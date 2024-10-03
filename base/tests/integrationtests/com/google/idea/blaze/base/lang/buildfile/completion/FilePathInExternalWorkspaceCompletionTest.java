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
package com.google.idea.blaze.base.lang.buildfile.completion;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.ExternalWorkspaceFixture;
import com.google.idea.blaze.base.lang.buildfile.BuildFileIntegrationTestCase;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.model.ExternalWorkspaceData;
import com.google.idea.blaze.base.model.primitives.ExternalWorkspace;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.openapi.editor.Editor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests file path code completion in BUILD file labels to paths inside visible external workspaces.
 */
@RunWith(JUnit4.class)
public class FilePathInExternalWorkspaceCompletionTest extends BuildFileIntegrationTestCase {
  protected ExternalWorkspaceFixture workspace;

  @Override
  protected ExternalWorkspaceData mockExternalWorkspaceData() {
    workspace =
        createExternalWorkspaceFixture(
            ExternalWorkspace.create("workspace_two", "com_workspace_two"));

    return ExternalWorkspaceData.create(ImmutableList.of(workspace.workspace));
  }

  @Test
  public void testUniqueDirectoryCompleted() throws Throwable {
    workspace.createBuildFile(new WorkspacePath("java/BUILD"), "'//'");

    BuildFile file =
        createBuildFileWithCaret(new WorkspacePath("file/BUILD"), "'@com_workspace_two//<caret>");

    assertThat(editorTest.completeIfUnique()).isTrue();
    assertFileContents(file, "'@com_workspace_two//java'");
    // check caret remains inside closing quote
    editorTest.assertCaretPosition(
        testFixture.getEditor(), 0, "'@com_workspace_two//java".length());
  }

  @Test
  public void testInsertPairQuoteOptionRespected() throws Throwable {
    workspace.createBuildFile(new WorkspacePath("java/BUILD"), "'//'");

    boolean old = CodeInsightSettings.getInstance().AUTOINSERT_PAIR_QUOTE;
    BuildFile file;
    try {
      CodeInsightSettings.getInstance().AUTOINSERT_PAIR_QUOTE = false;
      file =
          createBuildFileWithCaret(
              new WorkspacePath("file1/BUILD"), "'@com_workspace_two//j<caret>");
      assertThat(editorTest.completeIfUnique()).isTrue();
      assertFileContents(file, "'@com_workspace_two//java");

      CodeInsightSettings.getInstance().AUTOINSERT_PAIR_QUOTE = true;
      file =
          createBuildFileWithCaret(
              new WorkspacePath("file2/BUILD"), "'@com_workspace_two//j<caret>");
      assertThat(editorTest.completeIfUnique()).isTrue();
      assertFileContents(file, "'@com_workspace_two//java'");
    } finally {
      CodeInsightSettings.getInstance().AUTOINSERT_PAIR_QUOTE = old;
    }
  }

  @Test
  public void testUniqueMultiSegmentDirectoryCompleted() throws Throwable {
    workspace.createBuildFile(new WorkspacePath("java/com/google/BUILD"), "'//'");

    BuildFile file =
        createBuildFileWithCaret(new WorkspacePath("BUILD"), "'@com_workspace_two//<caret>");

    assertThat(editorTest.completeIfUnique()).isTrue();
    assertFileContents(file, "'@com_workspace_two//java/com/google'");
  }

  @Test
  public void testStopDirectoryTraversalAtBuildPackage() throws Throwable {
    workspace.createBuildFile(new WorkspacePath("foo/bar/BUILD"));
    workspace.createBuildFile(new WorkspacePath("foo/bar/baz/BUILD"));

    BuildFile file =
        createBuildFileWithCaret(new WorkspacePath("file/BUILD"), "'@com_workspace_two//f<caret>");
    assertThat(editorTest.completeIfUnique()).isTrue();

    assertFileContents(file, "'@com_workspace_two//foo/bar'");
  }

  // expected to be a typical workflow -- complete a segment,
  // get the possibilities, then start typing
  // next segment and complete again
  @Test
  public void testMultiStageCompletion() throws Throwable {
    workspace.createDirectory(new WorkspacePath("foo"));
    workspace.createDirectory(new WorkspacePath("bar"));
    workspace.createDirectory(new WorkspacePath("other"));
    workspace.createDirectory(new WorkspacePath("other/foo"));
    workspace.createDirectory(new WorkspacePath("other/bar"));

    BuildFile file =
        createBuildFileWithCaret(new WorkspacePath("file/BUILD"), "'@com_workspace_two//<caret>'");

    String[] completionItems = editorTest.getCompletionItemsAsStrings();
    assertThat(completionItems).hasLength(3);

    Editor editor = testFixture.getEditor();
    editorTest.performTypingAction(editor, 'o');
    assertThat(editorTest.completeIfUnique()).isTrue();
    assertFileContents(file, "'@com_workspace_two//other'");
    editorTest.assertCaretPosition(editor, 0, "'@com_workspace_two//other".length());

    editorTest.performTypingAction(editor, '/');
    editorTest.performTypingAction(editor, 'f');
    assertThat(editorTest.completeIfUnique()).isTrue();
    assertFileContents(file, "'@com_workspace_two//other/foo'");
    editorTest.assertCaretPosition(editor, 0, "'@com_workspace_two//other/foo".length());
  }

  @Test
  public void testCompletionSuggestionString() {
    workspace.createDirectory(new WorkspacePath("foo"));
    workspace.createDirectory(new WorkspacePath("bar"));
    workspace.createDirectory(new WorkspacePath("other"));
    workspace.createDirectory(new WorkspacePath("ostrich/foo"));
    workspace.createDirectory(new WorkspacePath("ostrich/fooz"));

    BuildFile file =
        createBuildFileWithCaret(new WorkspacePath("BUILD"), "'@com_workspace_two//o<caret>'");

    String[] completionItems = editorTest.getCompletionItemsAsSuggestionStrings();
    assertThat(completionItems).asList().containsExactly("other", "ostrich");

    editorTest.performTypingAction(testFixture.getEditor(), 's');

    assertThat(editorTest.completeIfUnique()).isTrue();
    assertFileContents(file, "'@com_workspace_two//ostrich'");

    completionItems = editorTest.getCompletionItemsAsSuggestionStrings();
    assertThat(completionItems).asList().containsExactly("/foo", "/fooz");

    editorTest.performTypingAction(testFixture.getEditor(), '/');

    completionItems = editorTest.getCompletionItemsAsSuggestionStrings();
    assertThat(completionItems).asList().containsExactly("foo", "fooz");

    editorTest.performTypingAction(testFixture.getEditor(), 'f');

    completionItems = editorTest.getCompletionItemsAsSuggestionStrings();
    assertThat(completionItems).asList().containsExactly("foo", "fooz");
  }
}
