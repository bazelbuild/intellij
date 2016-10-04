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
package com.google.idea.blaze.base.lang.buildfile.validation;

import static com.google.common.truth.Truth.assertThat;

import com.google.idea.blaze.base.lang.buildfile.BuildFileIntegrationTestCase;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.lang.buildfile.psi.GlobExpression;
import com.google.idea.blaze.base.lang.buildfile.psi.util.PsiUtils;
import com.intellij.codeInsight.daemon.impl.AnnotationHolderImpl;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.AnnotationSession;
import com.intellij.psi.PsiFile;
import java.util.List;
import java.util.stream.Collectors;

/** Tests glob validation. */
public class GlobValidationTest extends BuildFileIntegrationTestCase {

  public void testNormalGlob() {
    BuildFile file = createBuildFile("java/com/google/BUILD", "glob(['**/*.java'])");

    assertNoErrors(file);
  }

  public void testNamedIncludeArgument() {
    BuildFile file = createBuildFile("java/com/google/BUILD", "glob(include = ['**/*.java'])");

    assertNoErrors(file);
  }

  public void testAllArguments() {
    BuildFile file =
        createBuildFile(
            "java/com/google/BUILD",
            "glob(['**/*.java'], exclude = ['test/*.java'], exclude_directories = 0)");

    assertNoErrors(file);
  }

  public void testEmptyExcludeList() {
    BuildFile file = createBuildFile("java/com/google/BUILD", "glob(['**/*.java'], exclude = [])");

    assertNoErrors(file);
  }

  public void testNoIncludesError() {
    BuildFile file = createBuildFile("java/com/google/BUILD", "glob(exclude = ['BUILD'])");

    assertHasError(file, "Glob expression must contain at least one included string");
  }

  public void testSingletonExcludeArgumentError() {
    BuildFile file =
        createBuildFile("java/com/google/BUILD", "glob(['**/*.java'], exclude = 'BUILD')");

    assertHasError(file, "Glob parameter 'exclude' must be a list of strings");
  }

  public void testSingletonIncludeArgumentError() {
    BuildFile file = createBuildFile("java/com/google/BUILD", "glob(include = '**/*.java')");

    assertHasError(file, "Glob parameter 'include' must be a list of strings");
  }

  public void testInvalidExcludeDirectoriesValue() {
    BuildFile file =
        createBuildFile(
            "java/com/google/BUILD",
            "glob(['**/*.java'], exclude = ['test/*.java'], exclude_directories = true)");

    assertHasError(file, "exclude_directories parameter to glob must be 0 or 1");
  }

  public void testUnrecognizedArgumentError() {
    BuildFile file =
        createBuildFile(
            "java/com/google/BUILD", "glob(['**/*.java'], exclude = ['test/*.java'], extra = 1)");

    assertHasError(file, "Unrecognized glob argument");
  }

  public void testInvalidListArgumentValue() {
    BuildFile file = createBuildFile("java/com/google/BUILD", "glob(include = foo)");

    assertHasError(file, "Glob parameter 'include' must be a list of strings");
  }

  public void testLocalVariableReference() {
    BuildFile file =
        createBuildFile("java/com/google/BUILD", "foo = ['*.java']", "glob(include = foo)");

    assertNoErrors(file);
  }

  public void testLoadedVariableReference() {
    BuildFile ext = createBuildFile("java/com/foo/vars.bzl", "LIST_VAR = ['*']");
    BuildFile file =
        createBuildFile(
            "java/com/google/BUILD",
            "load('//java/com/foo:vars.bzl', 'LIST_VAR')",
            "glob(include = LIST_VAR)");

    assertNoErrors(file);
  }

  public void testInvalidLoadedVariableReference() {
    BuildFile ext = createBuildFile("java/com/foo/vars.bzl", "LIST_VAR = ['*']", "def function()");
    BuildFile file =
        createBuildFile(
            "java/com/google/BUILD",
            "load('//java/com/foo:vars.bzl', 'LIST_VAR', 'function')",
            "glob(include = function)");

    assertHasError(file, "Glob parameter 'include' must be a list of strings");
  }

  public void testUnresolvedReferenceExpression() {
    BuildFile file = createBuildFile("java/com/google/BUILD", "glob(include = ref)");

    assertHasError(file, "Glob parameter 'include' must be a list of strings");
  }

  public void testPossibleListExpressionFuncallExpression() {
    BuildFile file = createBuildFile("java/com/google/BUILD", "glob(include = fn.list)");

    assertNoErrors(file);
  }

  public void testPossibleListExpressionParameter() {
    BuildFile file =
        createBuildFile(
            "java/com/google/BUILD", "def function(param1, param2):", "    glob(include = param1)");

    assertNoErrors(file);
  }

  public void testNestedGlobs() {
    // blaze accepts nested globs
    BuildFile file = createBuildFile("java/com/google/BUILD", "glob(glob(['*.java']))");

    assertNoErrors(file);
  }

  public void testKnownInvalidResolvedListExpression() {
    BuildFile file =
        createBuildFile("java/com/google/BUILD", "bool_literal = True", "glob(bool_literal)");

    assertHasError(file, "Glob parameter 'include' must be a list of strings");
  }

  public void testKnownInvalidResolvedString() {
    BuildFile file =
        createBuildFile("java/com/google/BUILD", "bool_literal = True", "glob([bool_literal])");

    assertHasError(file, "Glob parameter 'include' must be a list of strings");
  }

  public void testPossibleStringLiteralIfStatement() {
    BuildFile file =
        createBuildFile("java/com/google/BUILD", "glob(include = ['*.java', if test : a else b])");

    // we don't know what the IfStatement evaluates to
    assertNoErrors(file);
  }

  public void testPossibleStringLiteralParameter() {
    BuildFile file =
        createBuildFile(
            "java/com/google/BUILD",
            "def function(param1, param2):",
            "    glob(include = [param1])");

    assertNoErrors(file);
  }

  private void assertNoErrors(BuildFile file) {
    assertThat(validateFile(file)).isEmpty();
  }

  private void assertHasError(BuildFile file, String error) {
    assertHasError(validateFile(file), error);
  }

  private void assertHasError(List<Annotation> annotations, String error) {
    List<String> messages =
        annotations.stream().map(Annotation::getMessage).collect(Collectors.toList());

    assertThat(messages).contains(error);
  }

  private List<Annotation> validateFile(BuildFile file) {
    GlobErrorAnnotator annotator = createAnnotator(file);
    for (GlobExpression glob :
        PsiUtils.findAllChildrenOfClassRecursive(file, GlobExpression.class)) {
      annotator.visitGlobExpression(glob);
    }
    return annotationHolder;
  }

  private GlobErrorAnnotator createAnnotator(PsiFile file) {
    annotationHolder = new AnnotationHolderImpl(new AnnotationSession(file));
    return new GlobErrorAnnotator() {
      @Override
      protected AnnotationHolder getHolder() {
        return annotationHolder;
      }
    };
  }

  private AnnotationHolderImpl annotationHolder = null;
}
