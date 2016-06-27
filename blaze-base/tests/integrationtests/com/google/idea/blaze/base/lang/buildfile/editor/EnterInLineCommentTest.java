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
package com.google.idea.blaze.base.lang.buildfile.editor;

import com.google.idea.blaze.base.lang.buildfile.BuildFileIntegrationTestCase;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.intellij.openapi.editor.Editor;

/**
 * Test that comments are continued when creating a newline mid comment.
 */
public class EnterInLineCommentTest extends BuildFileIntegrationTestCase {

  public void testInternalNewlineCommented() {
    BuildFile file = createBuildFile(
      "BUILD",
      "# first line comment",
      "# second line comment");

    Editor editor = openFileInEditor(file.getVirtualFile());
    setCaretPosition(editor, 1, "# second ".length());
    performTypingAction(editor, '\n');
    assertFileContents(
      file,
      "# first line comment",
      "# second ",
      "# line comment");
    assertCaretPosition(editor, 2, 2);
  }

  public void testNewlineAtEndOfComment() {
    BuildFile file = createBuildFile(
      "BUILD",
      "# first line comment",
      "# second line comment");

    Editor editor = openFileInEditor(file.getVirtualFile());
    setCaretPosition(editor, 1, "# second line comment".length());
    performTypingAction(editor, '\n');
    assertFileContents(
      file,
      "# first line comment",
      "# second line comment",
      "");
    assertCaretPosition(editor, 2, 0);
  }
}
