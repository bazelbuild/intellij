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
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.editor.Editor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests code completion of rule target labels. */
@RunWith(JUnit4.class)
public class RuleTargetCompletionTest extends BuildFileIntegrationTestCase {

  @Test
  public void testLocalTarget() {
    BuildFile file =
        createBuildFile(
            "java/com/google/BUILD",
            "java_library(name = 'lib')",
            "java_library(",
            "    name = 'test',",
            "    deps = [':']");

    Editor editor = openFileInEditor(file);
    setCaretPosition(editor, 3, "    deps = [':".length());

    LookupElement[] completionItems = testFixture.completeBasic();
    assertThat(completionItems).hasLength(1);
    assertThat(completionItems[0].toString()).isEqualTo("':lib'");
  }

  @Test
  public void testIgnoreContainingTarget() {
    BuildFile file =
        createBuildFile(
            "java/com/google/BUILD", "java_library(", "    name = 'lib',", "    deps = [':']");

    Editor editor = openFileInEditor(file);
    setCaretPosition(editor, 2, "    deps = [':".length());

    LookupElement[] completionItems = testFixture.completeBasic();
    assertThat(completionItems).isEmpty();
  }

  @Test
  public void testNotCodeCompletionInNameField() {
    BuildFile file =
        createBuildFile(
            "java/com/google/BUILD",
            "java_library(name = 'lib')",
            "java_library(",
            "    name = 'l'",
            ")");

    Editor editor = openFileInEditor(file);
    setCaretPosition(editor, 2, "    name = 'l".length());

    String[] completionItems = getCompletionItemsAsStrings();
    assertThat(completionItems).isEmpty();
  }

  @Test
  public void testNonLocalTarget() {
    createBuildFile("java/com/google/foo/BUILD", "java_library(name = 'foo_lib')");

    BuildFile bar =
        createBuildFile(
            "java/com/google/bar/BUILD",
            "java_library(",
            "    name = 'bar_lib',",
            "    deps = '//java/com/google/foo:')");

    Editor editor = openFileInEditor(bar);
    setCaretPosition(editor, 2, "    deps = '//java/com/google/foo:".length());

    String[] completionItems = getCompletionItemsAsStrings();
    assertThat(completionItems).asList().containsExactly("'//java/com/google/foo:foo_lib'");
  }

  @Test
  public void testNonLocalRulesNotCompletedWithoutColon() {
    createBuildFile("java/com/google/foo/BUILD", "java_library(name = 'foo_lib')");

    BuildFile bar =
        createBuildFile(
            "java/com/google/bar/BUILD",
            "java_library(",
            "    name = 'bar_lib',",
            "    deps = '//java/com/google/foo')");

    Editor editor = openFileInEditor(bar);
    setCaretPosition(editor, 2, "    deps = '//java/com/google/foo".length());

    String[] completionItems = getCompletionItemsAsStrings();
    assertThat(completionItems).isEmpty();
  }

  @Test
  public void testPackageLocalRulesCompletedWithoutColon() {
    BuildFile file =
        createBuildFile(
            "java/com/google/BUILD",
            "java_library(name = 'lib')",
            "java_library(",
            "    name = 'test',",
            "    deps = ['']");

    Editor editor = openFileInEditor(file);
    setCaretPosition(editor, 3, "    deps = ['".length());

    assertThat(completeIfUnique()).isTrue();
    assertFileContents(
        file,
        "java_library(name = 'lib')",
        "java_library(",
        "    name = 'test',",
        "    deps = ['lib']");
  }

  @Test
  public void testLocalPathIgnoredForNonLocalLabels() {
    createBuildFile("java/BUILD", "java_library(name = 'root_rule')");

    BuildFile otherPackage =
        createBuildFile(
            "java/com/google/BUILD",
            "java_library(",
            "java_library(name = 'other_rule')",
            "    name = 'lib',",
            "    deps = ['//java:']");

    Editor editor = openFileInEditor(otherPackage);
    setCaretPosition(editor, 3, "    deps = ['//java:".length());

    String[] completionItems = getCompletionItemsAsStrings();
    assertThat(completionItems).asList().contains("'//java:root_rule'");
    assertThat(completionItems).asList().doesNotContain("'//java/com/google:other_rule'");
  }
}
