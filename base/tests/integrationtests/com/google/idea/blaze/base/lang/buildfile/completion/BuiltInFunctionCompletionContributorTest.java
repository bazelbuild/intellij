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

import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.base.lang.buildfile.BuildFileIntegrationTestCase;
import com.google.idea.blaze.base.lang.buildfile.language.semantics.BuildLanguageSpec;
import com.google.idea.blaze.base.lang.buildfile.language.semantics.BuildLanguageSpecProvider;
import com.google.idea.blaze.base.lang.buildfile.language.semantics.RuleDefinition;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import javax.annotation.Nullable;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests BuiltInFunctionCompletionContributor */
@RunWith(JUnit4.class)
public class BuiltInFunctionCompletionContributorTest extends BuildFileIntegrationTestCase {

  private MockBuildLanguageSpecProvider specProvider;

  @Before
  public final void before() {
    specProvider = new MockBuildLanguageSpecProvider();
    registerApplicationService(BuildLanguageSpecProvider.class, specProvider);
  }

  @Test
  public void testSimpleTopLevelCompletion() {
    setRules("java_library", "android_binary");

    BuildFile file = createBuildFile(new WorkspacePath("BUILD"), "");

    Editor editor = editorTest.openFileInEditor(file.getVirtualFile());
    editorTest.setCaretPosition(editor, 0, 0);

    LookupElement[] completionItems = testFixture.completeBasic();
    assertThat(completionItems).hasLength(2);
    assertThat(completionItems[0].getLookupString()).isEqualTo("android_binary");
    assertThat(completionItems[1].getLookupString()).isEqualTo("java_library");

    assertFileContents(file, "");
  }

  @Test
  public void testUniqueTopLevelCompletion() {
    setRules("java_library", "android_binary");

    BuildFile file = createBuildFile(new WorkspacePath("BUILD"), "ja");

    Editor editor = editorTest.openFileInEditor(file.getVirtualFile());
    editorTest.setCaretPosition(editor, 0, 2);

    LookupElement[] completionItems = testFixture.completeBasic();
    assertThat(completionItems).isNull();

    assertFileContents(file, "java_library()");
    editorTest.assertCaretPosition(editor, 0, "java_library(".length());
  }

  @Test
  public void testSkylarkNativeCompletion() {
    setRules("java_library", "android_binary");

    BuildFile file =
        createBuildFile(new WorkspacePath("build_defs.bzl"), "def function():", "  native.j");

    Editor editor = editorTest.openFileInEditor(file.getVirtualFile());
    editorTest.setCaretPosition(editor, 1, "  native.j".length());

    LookupElement[] completionItems = testFixture.completeBasic();
    assertThat(completionItems).isNull();

    assertFileContents(file, "def function():", "  native.java_library()");
    editorTest.assertCaretPosition(editor, 1, "  native.java_library(".length());
  }

  @Test
  public void testNoCompletionInsideRule() {
    setRules("java_library", "android_binary");

    String[] contents = {"java_library(", "    name = \"lib\"", ""};

    BuildFile file = createBuildFile(new WorkspacePath("BUILD"), contents);

    Editor editor = editorTest.openFileInEditor(file.getVirtualFile());
    editorTest.setCaretPosition(editor, 2, 0);

    LookupElement[] completionItems = testFixture.completeBasic();
    assertThat(completionItems).isEmpty();
    assertFileContents(file, contents);
  }

  @Test
  public void testNoCompletionInComment() {
    setRules("java_library", "android_binary");

    BuildFile file = createBuildFile(new WorkspacePath("BUILD"), "#java");

    Editor editor = editorTest.openFileInEditor(file.getVirtualFile());
    editorTest.setCaretPosition(editor, 0, "#java".length());

    assertThat(editorTest.getCompletionItemsAsStrings()).isEmpty();
  }

  private void setRules(String... ruleNames) {
    ImmutableMap.Builder<String, RuleDefinition> rules = ImmutableMap.builder();
    for (String name : ruleNames) {
      rules.put(name, new RuleDefinition(name, ImmutableMap.of(), null));
    }
    specProvider.setRules(rules.build());
  }

  private static class MockBuildLanguageSpecProvider implements BuildLanguageSpecProvider {

    BuildLanguageSpec languageSpec;

    void setRules(ImmutableMap<String, RuleDefinition> rules) {
      languageSpec = new BuildLanguageSpec(rules);
    }

    @Nullable
    @Override
    public BuildLanguageSpec getLanguageSpec(Project project) {
      return languageSpec;
    }
  }
}
