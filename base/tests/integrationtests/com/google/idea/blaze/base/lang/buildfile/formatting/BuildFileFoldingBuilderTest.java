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
package com.google.idea.blaze.base.lang.buildfile.formatting;

import static com.google.common.truth.Truth.assertThat;

import com.google.idea.blaze.base.lang.buildfile.BuildFileIntegrationTestCase;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.lang.buildfile.psi.LoadStatement;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.editor.Editor;

/** Tests for {@link BuildFileFoldingBuilder}. */
public class BuildFileFoldingBuilderTest extends BuildFileIntegrationTestCase {

  public void testEndOfFileFunctionDelcaration() {
    // bug 28618935: test no NPE in the case where there's no
    // statement list following the func-def colon
    BuildFile file = createBuildFile("java/com/google/BUILD", "def function():");

    getFoldingRegions(file);
  }

  public void testFuncDefStatementsFolded() {
    BuildFile file =
        createBuildFile(
            "java/com/google/BUILD",
            "# multi-line comment, not folded",
            "# second line of comment",
            "def function(arg1, arg2):",
            "    stmt1",
            "    stmt2",
            "",
            "variable = 1");

    FoldingDescriptor[] foldingRegions = getFoldingRegions(file);
    assertThat(foldingRegions).hasLength(1);
    assertThat(foldingRegions[0].getElement().getPsi())
        .isEqualTo(file.findFunctionInScope("function"));
  }

  public void testRulesFolded() {
    BuildFile file =
        createBuildFile(
            "java/com/google/BUILD",
            "java_library(",
            "    name = 'lib',",
            "    srcs = glob(['*.java']),",
            ")");

    FoldingDescriptor[] foldingRegions = getFoldingRegions(file);
    assertThat(foldingRegions).hasLength(1);
    assertThat(foldingRegions[0].getElement().getPsi()).isEqualTo(file.findRule("lib"));
  }

  public void testLoadStatementFolded() {
    BuildFile file =
        createBuildFile(
            "java/com/google/BUILD",
            "load(",
            "   '//java/com/foo/build_defs.bzl',",
            "   'function1',",
            "   'function2',",
            ")");

    FoldingDescriptor[] foldingRegions = getFoldingRegions(file);
    assertThat(foldingRegions).hasLength(1);
    assertThat(foldingRegions[0].getElement().getPsi())
        .isEqualTo(file.findChildByClass(LoadStatement.class));
  }

  private FoldingDescriptor[] getFoldingRegions(BuildFile file) {
    Editor editor = openFileInEditor(file.getVirtualFile());
    return new BuildFileFoldingBuilder().buildFoldRegions(file.getNode(), editor.getDocument());
  }
}
