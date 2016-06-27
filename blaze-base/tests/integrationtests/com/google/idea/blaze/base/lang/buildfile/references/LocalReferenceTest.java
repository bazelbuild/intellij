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
package com.google.idea.blaze.base.lang.buildfile.references;

import com.google.idea.blaze.base.lang.buildfile.BuildFileIntegrationTestCase;
import com.google.idea.blaze.base.lang.buildfile.psi.*;
import com.intellij.psi.PsiElement;

import static com.google.common.truth.Truth.assertThat;

/**
 * Tests that local references (to TargetExpressions within a given file) are correctly resolved.
 */
public class LocalReferenceTest extends BuildFileIntegrationTestCase {

  public void testCreatesReference() {
    BuildFile file = createBuildFile(
      "java/com/google/BUILD",
      "a = 1",
      "c = a");

    AssignmentStatement[] stmts = file.childrenOfClass(AssignmentStatement.class);
    assertThat(stmts).hasLength(2);
    assertThat(stmts[1].getAssignedValue()).isInstanceOf(ReferenceExpression.class);

    ReferenceExpression ref = (ReferenceExpression) stmts[1].getAssignedValue();
    assertThat(ref.getReference()).isInstanceOf(LocalReference.class);
  }

  public void testReferenceResolves() {
    BuildFile file = createBuildFile(
      "java/com/google/BUILD",
      "a = 1",
      "c = a");

    AssignmentStatement[] stmts = file.childrenOfClass(AssignmentStatement.class);
    ReferenceExpression ref = (ReferenceExpression) stmts[1].getAssignedValue();

    PsiElement referencedElement = ref.getReferencedElement();
    assertThat(referencedElement).isEqualTo(stmts[0].getLeftHandSideExpression());
  }

  public void testTargetInOuterScope() {
    BuildFile file = createBuildFile(
      "java/com/google/BUILD",
      "a = 1",
      "function(c = a)");

    TargetExpression target = file.findChildByClass(TargetExpression.class);
    FuncallExpression funcall = file.findChildByClass(FuncallExpression.class);
    ReferenceExpression ref = funcall.firstChildOfClass(ReferenceExpression.class);
    assertThat(ref.getReferencedElement()).isEqualTo(target);
  }

}
