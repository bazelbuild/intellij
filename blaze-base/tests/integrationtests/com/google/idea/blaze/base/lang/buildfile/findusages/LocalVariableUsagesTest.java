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
import com.google.idea.blaze.base.lang.buildfile.psi.util.PsiUtils;
import com.google.idea.blaze.base.lang.buildfile.references.LocalReference;
import com.google.idea.blaze.base.lang.buildfile.references.TargetReference;
import com.google.idea.blaze.base.lang.buildfile.search.FindUsages;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;

import static com.google.common.truth.Truth.assertThat;

/**
 * Tests that references to local variables are found by the 'Find Usages' action
 * TODO: Support comprehension suffix, and add test for it
 */
public class LocalVariableUsagesTest extends BuildFileIntegrationTestCase {

  public void testLocalReferences() {
    BuildFile buildFile = createBuildFile(
      "java/com/google/BUILD",
      "localVar = 5",
      "funcall(localVar)",
      "def function(name):",
      "    tempVar = localVar");

    TargetExpression target = buildFile
      .findChildByClass(AssignmentStatement.class)
      .getLeftHandSideExpression();

    PsiReference[] references = FindUsages.findAllReferences(target);
    assertThat(references).hasLength(2);

    FuncallExpression funcall = buildFile.findChildByClass(FuncallExpression.class);
    assertThat(funcall).isNotNull();

    PsiElement firstRef = references[0].getElement();
    assertThat(PsiUtils.getParentOfType(firstRef, FuncallExpression.class))
      .isEqualTo(funcall);

    FunctionStatement function = buildFile.findChildByClass(FunctionStatement.class);
    assertThat(function).isNotNull();

    PsiElement secondRef = references[1].getElement();
    assertThat(secondRef.getParent()).isInstanceOf(AssignmentStatement.class);
    assertThat(PsiUtils.getParentOfType(secondRef, FunctionStatement.class))
      .isEqualTo(function);
  }

  // the case where a symbol is the target of multiple assignment statements
  public void testMultipleAssignments() {
    BuildFile buildFile = createBuildFile(
      "java/com/google/BUILD",
      "var = 5",
      "var += 1",
      "var = 0");

    TargetExpression target = buildFile
      .findChildByClass(AssignmentStatement.class)
      .getLeftHandSideExpression();

    PsiReference[] references = FindUsages.findAllReferences(target);
    assertThat(references).hasLength(2);

    assertThat(references[0]).isInstanceOf(LocalReference.class);
    assertThat(references[1]).isInstanceOf(TargetReference.class);
  }

}
