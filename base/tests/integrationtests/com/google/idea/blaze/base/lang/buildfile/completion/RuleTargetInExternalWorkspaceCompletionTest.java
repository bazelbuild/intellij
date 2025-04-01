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
import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.base.ExternalWorkspaceFixture;
import com.google.idea.blaze.base.lang.buildfile.BuildFileIntegrationTestCase;
import com.google.idea.blaze.base.lang.buildfile.language.semantics.BuildLanguageSpec;
import com.google.idea.blaze.base.lang.buildfile.language.semantics.BuildLanguageSpecProvider;
import com.google.idea.blaze.base.lang.buildfile.language.semantics.RuleDefinition;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.model.ExternalWorkspaceData;
import com.google.idea.blaze.base.model.primitives.ExternalWorkspace;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import javax.annotation.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests code completion of rule target labels inside visible external workspaces. */
@RunWith(JUnit4.class)
public class RuleTargetInExternalWorkspaceCompletionTest extends BuildFileIntegrationTestCase {
  protected ExternalWorkspaceFixture workspace;

  @Override
  protected ExternalWorkspaceData mockExternalWorkspaceData() {
    workspace = createExternalWorkspaceFixture(ExternalWorkspace.create("workspace+", "workspace"));

    return ExternalWorkspaceData.create(ImmutableList.of(workspace.workspace));
  }

  @Test
  public void testCustomRuleCompletion() {
    MockBuildLanguageSpecProvider specProvider = new MockBuildLanguageSpecProvider();
    setBuildLanguageSpecRules(specProvider, "java_library");
    registerProjectService(BuildLanguageSpecProvider.class, specProvider);

    workspace.createBuildFile(new WorkspacePath("java/BUILD"), "custom_rule(name = 'lib')");

    createBuildFileWithCaret(
        new WorkspacePath("java/com/google/BUILD"),
        "java_library(",
        "    name = 'test',",
        "    deps = ['@workspace//java:<caret>']");

    assertThat(editorTest.getCompletionItemsAsStrings())
        .asList()
        .containsExactly("'@workspace//java:lib'");
  }

  @Test
  public void testWorkspaceTarget() {
    workspace.createBuildFile(
        new WorkspacePath("java/com/google/foo/BUILD"), "java_library(name = 'foo_lib')");

    createBuildFileWithCaret(
        new WorkspacePath("java/com/google/bar/BUILD"),
        "java_library(",
        "    name = 'bar_lib',",
        "    deps = '@workspace//java/com/google/foo:<caret>')");

    assertThat(editorTest.getCompletionItemsAsStrings())
        .asList()
        .containsExactly("'@workspace//java/com/google/foo:foo_lib'");
  }

  @Test
  public void testNotCompletedWithoutColon() {
    workspace.createBuildFile(
        new WorkspacePath("java/com/google/foo/BUILD"), "java_library(name = 'foo_lib')");

    BuildFile bar =
        createBuildFileWithCaret(
            new WorkspacePath("java/com/google/bar/BUILD"),
            "java_library(",
            "    name = 'bar_lib',",
            "    deps = '@workspace//java/com/google/foo<caret>')");

    String[] completionItems = editorTest.getCompletionItemsAsStrings();
    assertThat(completionItems).isEmpty();
  }

  @Test
  public void testLocalPathIgnored() {
    workspace.createBuildFile(new WorkspacePath("java/BUILD"), "java_library(name = 'root_rule')");

    createBuildFileWithCaret(
        new WorkspacePath("java/com/google/BUILD"),
        "java_library(name = 'other_rule')",
        "java_library(",
        "    name = 'lib',",
        "    deps = ['@workspace//java:<caret>']");

    String[] completionItems = editorTest.getCompletionItemsAsStrings();
    assertThat(completionItems).asList().contains("'@workspace//java:root_rule'");
    assertThat(completionItems).asList().doesNotContain("'//java/com/google:other_rule'");
  }

  private static void setBuildLanguageSpecRules(
      MockBuildLanguageSpecProvider specProvider, String... ruleNames) {
    ImmutableMap.Builder<String, RuleDefinition> rules = ImmutableMap.builder();
    for (String name : ruleNames) {
      rules.put(name, new RuleDefinition(name, ImmutableMap.of(), null));
    }
    specProvider.setRules(rules.build());
  }

  private static class MockBuildLanguageSpecProvider implements BuildLanguageSpecProvider {

    BuildLanguageSpec languageSpec = new BuildLanguageSpec(ImmutableMap.of());

    void setRules(ImmutableMap<String, RuleDefinition> rules) {
      languageSpec = new BuildLanguageSpec(rules);
    }

    @Nullable
    @Override
    public BuildLanguageSpec getLanguageSpec() {
      return languageSpec;
    }
  }
}
