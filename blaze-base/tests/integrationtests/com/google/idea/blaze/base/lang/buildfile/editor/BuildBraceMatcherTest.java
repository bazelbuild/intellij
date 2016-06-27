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

import com.google.common.base.Joiner;
import com.google.idea.blaze.base.lang.buildfile.BuildFileIntegrationTestCase;
import com.intellij.psi.PsiFile;

/**
 * Test brace matching (auto-inserting closing braces when appropriate)
 */
public class BuildBraceMatcherTest extends BuildFileIntegrationTestCase {

  private PsiFile setInput(String... fileContents) {
    return testFixture.configureByText("BUILD", Joiner.on("\n").join(fileContents));
  }

  public void testClosingParenInserted() {
    PsiFile file = setInput(
      "java_library<caret>"
    );

    performTypingAction(testFixture.getEditor(), '(');

    assertFileContents(
      file,
      "java_library()"
    );
  }

  public void testClosingBraceInserted() {
    PsiFile file = setInput(
      "<caret>"
    );

    performTypingAction(testFixture.getEditor(), '{');

    assertFileContents(
      file,
      "{}"
    );
  }


  public void testClosingBracketInserted() {
    PsiFile file = setInput(
      "<caret>"
    );

    performTypingAction(testFixture.getEditor(), '[');

    assertFileContents(
      file,
      "[]"
    );
  }

  public void testNoClosingBracketInsertedIfLaterDanglingRBracket() {
    PsiFile file = setInput(
      "java_library(",
      "    srcs =<caret> 'source.java']",
      ")"
    );

    performTypingAction(testFixture.getEditor(), '[');

    assertFileContents(
      file,
      "java_library(",
      "    srcs =[ 'source.java']",
      ")"
    );
  }

  public void testClosingBracketInsertedIfFollowedByWhitespace() {
    PsiFile file = setInput(
      "java_library(",
      "    srcs =<caret> 'source.java'",
      ")"
    );

    performTypingAction(testFixture.getEditor(), '[');

    assertFileContents(
      file,
      "java_library(",
      "    srcs =[] 'source.java'",
      ")"
    );
  }

  public void testNoClosingBraceInsertedWhenFollowedByIdentifier() {
    PsiFile file = setInput(
      "hello = <caret>test"
    );

    performTypingAction(testFixture.getEditor(), '(');

    assertFileContents(
      file,
      "hello = (test"
    );

    file = setInput(
      "hello = <caret>test"
    );

    performTypingAction(testFixture.getEditor(), '[');

    assertFileContents(
      file,
      "hello = [test"
    );

    file = setInput(
      "hello = <caret>test"
    );

    performTypingAction(testFixture.getEditor(), '{');

    assertFileContents(
      file,
      "hello = {test"
    );
  }

}
