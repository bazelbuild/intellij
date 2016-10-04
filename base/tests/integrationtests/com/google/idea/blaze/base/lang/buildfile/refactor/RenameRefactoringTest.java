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
package com.google.idea.blaze.base.lang.buildfile.refactor;

import static com.google.common.truth.Truth.assertThat;

import com.google.idea.blaze.base.lang.buildfile.BuildFileIntegrationTestCase;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.lang.buildfile.psi.FuncallExpression;
import com.google.idea.blaze.base.lang.buildfile.psi.FunctionStatement;
import com.google.idea.blaze.base.lang.buildfile.psi.Parameter;
import com.google.idea.blaze.base.lang.buildfile.psi.StringLiteral;
import com.google.idea.blaze.base.lang.buildfile.psi.TargetExpression;
import com.google.idea.blaze.base.lang.buildfile.psi.util.PsiUtils;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.rename.RenameDialog;
import com.intellij.refactoring.rename.RenamePsiElementProcessor;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Tests that BUILD file references are correctly updated when performing rename refactors. */
public class RenameRefactoringTest extends BuildFileIntegrationTestCase {

  public void testRenameJavaClass() {
    PsiFile javaFile =
        createPsiFile(
            "com/google/foo/JavaClass.java",
            "package com.google.foo;",
            "public class JavaClass {}");

    createBuildFile(
        "com/google/foo/BUILD",
        "java_library(name = \"ref1\", srcs = [\"//com/google/foo:JavaClass.java\"])",
        "java_library(name = \"ref2\", srcs = [\"JavaClass.java\"])",
        "java_library(name = \"ref3\", srcs = [\":JavaClass.java\"])");

    List<StringLiteral> references =
        findAllReferencingElementsOfType(javaFile, StringLiteral.class);

    Set<String> oldStrings =
        references.stream().map(StringLiteral::getStringContents).collect(Collectors.toSet());

    assertThat(references).hasSize(3);

    renamePsiElement(javaFile, "NewName.java");

    Set<String> newStrings =
        references.stream().map(StringLiteral::getStringContents).collect(Collectors.toSet());

    Set<String> expectedNewStrings =
        oldStrings
            .stream()
            .map((s) -> s.replaceAll("JavaClass", "NewName"))
            .collect(Collectors.toSet());

    assertThat(expectedNewStrings).containsExactlyElementsIn(newStrings);
  }

  public void testRenameRule() {
    BuildFile fooPackage =
        createBuildFile(
            "com/google/foo/BUILD",
            "rule_type(name = \"target\")",
            "java_library(name = \"local_ref\", srcs = [\":target\"])");

    BuildFile barPackage =
        createBuildFile(
            "com/google/test/bar/BUILD",
            "rule_type(name = \"ref\", arg = \"//com/google/foo:target\")",
            "top_level_ref = \"//com/google/foo:target\"");

    FuncallExpression targetRule =
        PsiUtils.findFirstChildOfClassRecursive(fooPackage, FuncallExpression.class);
    renamePsiElement(targetRule, "newTargetName");

    assertFileContents(
        fooPackage,
        "rule_type(name = \"newTargetName\")",
        "java_library(name = \"local_ref\", srcs = [\":newTargetName\"])");

    assertFileContents(
        barPackage,
        "rule_type(name = \"ref\", arg = \"//com/google/foo:newTargetName\")",
        "top_level_ref = \"//com/google/foo:newTargetName\"");
  }

  public void testRenameSkylarkExtension() {
    BuildFile extFile =
        createBuildFile("java/com/google/tools/build_defs.bzl", "def function(name, deps)");

    BuildFile buildFile =
        createBuildFile(
            "java/com/google/BUILD",
            "load(",
            "\"//java/com/google:tools/build_defs.bzl\",",
            "\"function\"",
            ")",
            "function(name = \"name\", deps = []");

    renamePsiElement(extFile, "skylark.bzl");

    assertFileContents(
        buildFile,
        "load(",
        "\"//java/com/google:tools/skylark.bzl\",",
        "\"function\"",
        ")",
        "function(name = \"name\", deps = []");
  }

  public void testRenameLoadedFunction() {
    BuildFile extFile =
        createBuildFile("java/com/google/tools/build_defs.bzl", "def function(name, deps)");

    BuildFile buildFile =
        createBuildFile(
            "java/com/google/BUILD",
            "load(",
            "\"//java/com/google/tools:build_defs.bzl\",",
            "\"function\"",
            ")",
            "function(name = \"name\", deps = []");

    FunctionStatement fn = extFile.findChildByClass(FunctionStatement.class);
    renamePsiElement(fn, "action");

    assertFileContents(extFile, "def action(name, deps)");

    assertFileContents(
        buildFile,
        "load(",
        "\"//java/com/google/tools:build_defs.bzl\",",
        "\"action\"",
        ")",
        "action(name = \"name\", deps = []");
  }

  public void testRenameLocalVariable() {
    BuildFile file = createBuildFile("java/com/google/BUILD", "a = 1", "c = a");

    TargetExpression target = PsiUtils.findFirstChildOfClassRecursive(file, TargetExpression.class);
    assertThat(target.getText()).isEqualTo("a");

    renamePsiElement(target, "b");

    assertFileContents(file, "b = 1", "c = b");
  }

  // all references, including path fragments in labels, should be renamed.
  public void testRenameDirectory() {
    createBuildFile("java/com/baz/BUILD");
    createBuildFile("java/com/google/tools/BUILD");
    BuildFile buildFile =
        createBuildFile(
            "java/com/google/BUILD",
            "load(",
            "\"//java/com/google/tools:build_defs.bzl\",",
            "\"function\"",
            ")",
            "function(name = \"name\", deps = [\"//java/com/baz:target\"]");

    renameDirectory("java/com", "java/alt");

    assertFileContents(
        buildFile,
        "load(",
        "\"//java/alt/google/tools:build_defs.bzl\",",
        "\"function\"",
        ")",
        "function(name = \"name\", deps = [\"//java/alt/baz:target\"]");
  }

  public void testRenameFunctionParameter() {
    BuildFile extFile =
        createBuildFile("java/com/google/tools/build_defs.bzl", "def function(name, deps)");

    BuildFile buildFile =
        createBuildFile(
            "java/com/google/BUILD",
            "load(",
            "\"//java/com/google/tools:build_defs.bzl\",",
            "\"function\"",
            ")",
            "function(name = \"name\", deps = []");

    FunctionStatement fn = extFile.findChildByClass(FunctionStatement.class);
    Parameter param = fn.getParameterList().findParameterByName("deps");
    renamePsiElement(param, "exports");

    assertFileContents(extFile, "def function(name, exports)");

    assertFileContents(
        buildFile,
        "load(",
        "\"//java/com/google/tools:build_defs.bzl\",",
        "\"function\"",
        ")",
        "function(name = \"name\", exports = []");
  }

  public void testRenameSuggestionForBuildFile() {
    BuildFile buildFile = createBuildFile("java/com/google/BUILD");
    RenamePsiElementProcessor processor = RenamePsiElementProcessor.forElement(buildFile);
    RenameDialog dialog = processor.createRenameDialog(getProject(), buildFile, buildFile, null);
    String[] suggestions = dialog.getSuggestedNames();
    assertThat(suggestions[0]).isEqualTo("BUILD");
  }

  public void testRenameSuggestionForSkylarkFile() {
    BuildFile buildFile = createBuildFile("java/com/google/tools/build_defs.bzl");
    RenamePsiElementProcessor processor = RenamePsiElementProcessor.forElement(buildFile);
    RenameDialog dialog = processor.createRenameDialog(getProject(), buildFile, buildFile, null);
    String[] suggestions = dialog.getSuggestedNames();
    assertThat(suggestions[0]).isEqualTo("build_defs.bzl");
  }

}
