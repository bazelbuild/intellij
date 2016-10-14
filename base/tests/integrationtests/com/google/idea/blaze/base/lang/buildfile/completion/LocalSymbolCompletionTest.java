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

import com.google.common.base.Joiner;
import com.google.idea.blaze.base.lang.buildfile.BuildFileIntegrationTestCase;
import com.intellij.psi.PsiFile;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests code completion works with general symbols in scope. */
@RunWith(JUnit4.class)
public class LocalSymbolCompletionTest extends BuildFileIntegrationTestCase {

  private PsiFile setInput(String... fileContents) {
    return testFixture.configureByText("BUILD", Joiner.on("\n").join(fileContents));
  }

  private void assertResult(String... resultingFileContents) {
    testFixture.getFile().getText();
    testFixture.checkResult(Joiner.on("\n").join(resultingFileContents));
  }

  @Test
  public void testLocalVariable() {
    setInput("var = [a, b]", "def function(name, deps, srcs):", "  v<caret>");

    completeIfUnique();

    assertResult("var = [a, b]", "def function(name, deps, srcs):", "  var<caret>");
  }

  @Test
  public void testLocalFunction() {
    setInput("def fnName():return True", "def function(name, deps, srcs):", "  fnN<caret>");

    completeIfUnique();

    assertResult("def fnName():return True", "def function(name, deps, srcs):", "  fnName<caret>");
  }

  @Test
  public void testNoCompletionAfterDot() {
    setInput("var = [a, b]", "def function(name, deps, srcs):", "  ext.v<caret>");

    String[] completionItems = getCompletionItemsAsStrings();
    assertThat(completionItems).isEmpty();
  }

  @Test
  public void testFunctionParam() {
    setInput("def test(var):", "  v<caret>");

    completeIfUnique();

    assertResult("def test(var):", "  var<caret>");
  }

  // b/28912523: when symbol is present in multiple assignment statements, should only be
  // included once in the code-completion dialog
  @Test
  public void testSymbolAssignedMultipleTimes() {
    setInput("var = 1", "var = 2", "var = 3", "<caret>");

    completeIfUnique();

    assertResult("var = 1", "var = 2", "var = 3", "var<caret>");
  }

  @Test
  public void testSymbolDefinedOutsideScope() {
    setInput("<caret>", "var = 1");

    assertThat(getCompletionItemsAsStrings()).isEmpty();
  }

  @Test
  public void testSymbolDefinedOutsideScope2() {
    setInput("def fn():", "  var = 1", "v<caret>");

    assertThat(testFixture.completeBasic()).isEmpty();
  }

  @Test
  public void testSymbolDefinedOutsideScope3() {
    setInput("for var in (1, 2, 3): print var", "v<caret>");

    assertThat(testFixture.completeBasic()).isEmpty();
  }
}
