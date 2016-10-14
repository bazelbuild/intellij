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
    BuildFile file = createBuildFile("java/BUILD", "'//'");

    Editor editor = openFileInEditor(file);
    setCaretPosition(editor, 0, "'//".length());

    assertThat(completeIfUnique()).isTrue();
    assertFileContents(file, "'//java'");
    // check caret remains inside closing quote
    assertCaretPosition(editor, 0, "'//java".length());
  }

  @Test
  public void testUniqueMultiSegmentDirectoryCompleted() {
    BuildFile file = createBuildFile("java/com/google/BUILD", "'//'");

    Editor editor = openFileInEditor(file);
    setCaretPosition(editor, 0, "'//".length());

    assertThat(completeIfUnique()).isTrue();
    assertFileContents(file, "'//java/com/google'");
  }

  // expected to be a typical workflow -- complete a segment,
  // get the possibilities, then start typing
  // next segment and complete again
  @Test
  public void testMultiStageCompletion() {
    createDirectory("foo");
    createDirectory("bar");
    createDirectory("other");
    createDirectory("other/foo");
    createDirectory("other/bar");

    BuildFile file = createBuildFile("BUILD", "'//'");

    Editor editor = openFileInEditor(file);
    setCaretPosition(editor, 0, "'//".length());

    String[] completionItems = getCompletionItemsAsStrings();
    assertThat(completionItems).hasLength(3);

    performTypingAction(editor, 'o');
    assertThat(completeIfUnique()).isTrue();
    assertFileContents(file, "'//other'");
    assertCaretPosition(editor, 0, "'//other".length());

    performTypingAction(editor, '/');
    performTypingAction(editor, 'f');
    assertThat(completeIfUnique()).isTrue();
    assertFileContents(file, "'//other/foo'");
    assertCaretPosition(editor, 0, "'//other/foo".length());
  }

  @Test
  public void testCompletionSuggestionString() {
    createDirectory("foo");
    createDirectory("bar");
    createDirectory("other");
    createDirectory("ostrich/foo");
    createDirectory("ostrich/fooz");

    VirtualFile file = createAndSetCaret("BUILD", "'//o<caret>'");

    String[] completionItems = getCompletionItemsAsSuggestionStrings();
    assertThat(completionItems).asList().containsExactly("other", "ostrich");

    performTypingAction(testFixture.getEditor(), 's');

    assertThat(completeIfUnique()).isTrue();
    assertFileContents(file, "'//ostrich'");

    completionItems = getCompletionItemsAsSuggestionStrings();
    assertThat(completionItems).asList().containsExactly("/foo", "/fooz");

    performTypingAction(testFixture.getEditor(), '/');

    completionItems = getCompletionItemsAsSuggestionStrings();
    assertThat(completionItems).asList().containsExactly("foo", "fooz");

    performTypingAction(testFixture.getEditor(), 'f');

    completionItems = getCompletionItemsAsSuggestionStrings();
    assertThat(completionItems).asList().containsExactly("foo", "fooz");
  }

  private VirtualFile createAndSetCaret(String filePath, String... fileContents) {
    VirtualFile file = createFile(filePath, fileContents);
    testFixture.configureFromExistingVirtualFile(file);
    return file;
  }
}
