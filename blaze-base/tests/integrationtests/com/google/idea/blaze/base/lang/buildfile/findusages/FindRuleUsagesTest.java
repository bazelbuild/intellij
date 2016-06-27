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
package com.google.idea.blaze.base.lang.buildfile.findusages;

import com.google.idea.blaze.base.lang.buildfile.BuildFileIntegrationTestCase;
import com.google.idea.blaze.base.lang.buildfile.psi.*;
import com.google.idea.blaze.base.lang.buildfile.search.FindUsages;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;

import static com.google.common.truth.Truth.assertThat;

/**
 * Tests that usages of build rules are found
 */
public class FindRuleUsagesTest extends BuildFileIntegrationTestCase {

  public void testLocalReferences() {
    BuildFile buildFile = createBuildFile(
      "java/com/google/BUILD",
      "java_library(name = \"target\")",
      "top_level_ref = \":target\"",
      "java_library(name = \"other\", deps = [\":target\"]");

    FuncallExpression target = buildFile.findChildByClass(FuncallExpression.class);

    PsiReference[] references = FindUsages.findAllReferences(target);
    assertThat(references).hasLength(2);

    PsiElement firstRef = references[0].getElement();
    assertThat(firstRef).isInstanceOf(StringLiteral.class);
    assertThat(firstRef.getParent()).isInstanceOf(AssignmentStatement.class);

    PsiElement secondRef = references[1].getElement();
    assertThat(secondRef).isInstanceOf(StringLiteral.class);
    assertThat(secondRef.getParent()).isInstanceOf(ListLiteral.class);
  }

  // test full package references, made locally
  public void testLocalFullReference() {
    BuildFile buildFile = createBuildFile(
      "java/com/google/BUILD",
      "java_library(name = \"target\")",
      "java_library(name = \"other\", deps = [\"//java/com/google:target\"]");

    FuncallExpression target = buildFile.findChildByClass(FuncallExpression.class);

    PsiReference[] references = FindUsages.findAllReferences(target);
    assertThat(references).hasLength(1);

    PsiElement ref = references[0].getElement();
    assertThat(ref).isInstanceOf(StringLiteral.class);
    assertThat(ref.getParent()).isInstanceOf(ListLiteral.class);
  }

  public void testNonLocalReferences() {
    BuildFile targetFile = createBuildFile(
      "java/com/google/foo/BUILD",
      "java_library(name = \"target\")");

    BuildFile refFile = createBuildFile(
      "java/com/google/bar/BUILD",
      "java_library(name = \"ref\", exports = [\"//java/com/google/foo:target\"])");

    FuncallExpression target = targetFile.findChildByClass(FuncallExpression.class);

    PsiReference[] references = FindUsages.findAllReferences(target);
    assertThat(references).hasLength(1);

    PsiElement ref = references[0].getElement();
    assertThat(ref).isInstanceOf(StringLiteral.class);
    assertThat(ref.getContainingFile()).isEqualTo(refFile);
  }

  public void testFindUsagesWorksFromNameString() {
    BuildFile targetFile = createBuildFile(
      "java/com/google/foo/BUILD",
      "java_library(name = \"tar<caret>get\")");

    BuildFile refFile = createBuildFile(
      "java/com/google/bar/BUILD",
      "java_library(name = \"ref\", exports = [\"//java/com/google/foo:target\"])");

    testFixture.configureFromExistingVirtualFile(targetFile.getVirtualFile());

    PsiElement targetElement = GotoDeclarationAction.findElementToShowUsagesOf(
      testFixture.getEditor(),
      testFixture.getEditor().getCaretModel().getOffset());

    PsiReference[] references = FindUsages.findAllReferences(targetElement);
    assertThat(references).hasLength(1);

    PsiElement ref = references[0].getElement();
    assertThat(ref).isInstanceOf(StringLiteral.class);
    assertThat(ref.getContainingFile()).isEqualTo(refFile);
  }

  public void testInvalidReferenceDoesntResolve() {
    // reference ":target" from another build file (missing package path in label)
    BuildFile targetFile = createBuildFile(
      "java/com/google/foo/BUILD",
      "java_library(name = \"target\")");

    BuildFile refFile = createBuildFile(
      "java/com/google/bar/BUILD",
      "java_library(name = \"ref\", exports = [\":target\"])");

    FuncallExpression target = targetFile.findChildByClass(FuncallExpression.class);
    assertThat(target).isNotNull();

    PsiReference[] references = FindUsages.findAllReferences(target);
    assertThat(references).hasLength(0);
  }

}
