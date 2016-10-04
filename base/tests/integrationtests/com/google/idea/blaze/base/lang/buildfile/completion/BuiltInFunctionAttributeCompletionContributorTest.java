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
import com.google.idea.blaze.base.lang.buildfile.language.semantics.AttributeDefinition;
import com.google.idea.blaze.base.lang.buildfile.language.semantics.BuildLanguageSpec;
import com.google.idea.blaze.base.lang.buildfile.language.semantics.BuildLanguageSpecProvider;
import com.google.idea.blaze.base.lang.buildfile.language.semantics.RuleDefinition;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.repackaged.devtools.build.lib.query2.proto.proto2api.Build;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import javax.annotation.Nullable;

/** Tests for BuiltInFunctionAttributeCompletionContributor. */
public class BuiltInFunctionAttributeCompletionContributorTest
    extends BuildFileIntegrationTestCase {

  private MockBuildLanguageSpecProvider specProvider;

  @Override
  protected void doSetup() {
    super.doSetup();
    specProvider = new MockBuildLanguageSpecProvider();
    registerApplicationService(BuildLanguageSpecProvider.class, specProvider);
  }

  public void testSimpleCompletion() {
    setRuleAndAttributes("sh_binary", "name", "deps", "srcs", "data");

    BuildFile file = createBuildFile("BUILD", "sh_binary(");

    Editor editor = openFileInEditor(file.getVirtualFile());
    setCaretPosition(editor, 0, "sh_binary(".length());

    String[] completionItems = getCompletionItemsAsStrings();
    assertThat(completionItems).asList().containsAllOf("name", "deps", "srcs", "data");
  }

  public void testSimpleSingleCompletion() {
    setRuleAndAttributes("sh_binary", "name", "deps", "srcs", "data");

    BuildFile file = createBuildFile("BUILD", "sh_binary(", "    n");

    Editor editor = openFileInEditor(file.getVirtualFile());
    setCaretPosition(editor, 1, "    n".length());

    String[] completionItems = getCompletionItemsAsStrings();
    assertThat(completionItems).isNull();
    assertFileContents(file, "sh_binary(", "    name");
  }

  public void testNoCompletionInUnknownRule() {
    setRuleAndAttributes("sh_binary", "name", "deps", "srcs", "data");

    BuildFile file = createBuildFile("BUILD", "java_binary(");

    Editor editor = openFileInEditor(file.getVirtualFile());
    setCaretPosition(editor, 0, "java_binary(".length());

    LookupElement[] completionItems = testFixture.completeBasic();
    assertThat(completionItems).isEmpty();
  }

  public void testNoCompletionInComment() {
    setRuleAndAttributes("sh_binary", "name", "deps", "srcs", "data");

    BuildFile file = createBuildFile("BUILD", "sh_binary(#");

    Editor editor = openFileInEditor(file.getVirtualFile());
    setCaretPosition(editor, 0, "sh_binary(#".length());
    assertEmpty(getCompletionItemsAsStrings());
  }

  public void testCompletionInSkylarkExtension() {
    setRuleAndAttributes("sh_binary", "name", "deps", "srcs", "data");

    BuildFile file = createBuildFile("skylark.bzl", "native.sh_binary(");

    Editor editor = openFileInEditor(file.getVirtualFile());
    setCaretPosition(editor, 0, "native.sh_binary(".length());

    String[] completionItems = getCompletionItemsAsStrings();
    assertThat(completionItems).asList().containsAllOf("name", "deps", "srcs", "data");
  }

  private void setRuleAndAttributes(String ruleName, String... attributes) {
    ImmutableMap.Builder<String, AttributeDefinition> map = ImmutableMap.builder();
    for (String attr : attributes) {
      map.put(
          attr,
          new AttributeDefinition(attr, Build.Attribute.Discriminator.UNKNOWN, false, null, null));
    }
    RuleDefinition rule = new RuleDefinition(ruleName, map.build(), null);
    specProvider.setRules(ImmutableMap.of(ruleName, rule));
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
