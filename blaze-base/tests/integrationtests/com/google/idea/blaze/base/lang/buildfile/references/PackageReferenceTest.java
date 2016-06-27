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
import com.google.idea.blaze.base.lang.buildfile.psi.Argument;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.lang.buildfile.psi.FuncallExpression;
import com.google.idea.blaze.base.lang.buildfile.psi.StringLiteral;
import com.google.idea.blaze.base.lang.buildfile.psi.util.PsiUtils;
import com.intellij.psi.PsiReference;

import static com.google.common.truth.Truth.assertThat;

/**
 * Tests that package references in string literals are correctly resolved.
 */
public class PackageReferenceTest extends BuildFileIntegrationTestCase {

  public void testDirectReferenceResolves() {
    BuildFile buildFile1 = createBuildFile(
      "java/com/google/tools/BUILD",
      "# contents");

    BuildFile buildFile2 = createBuildFile(
      "java/com/google/other/BUILD",
      "package_group(name = \"grp\", packages = [\"//java/com/google/tools\"])");

    Argument.Keyword packagesArg = buildFile2.firstChildOfClass(FuncallExpression.class).getArgList().getKeywordArgument("packages");
    StringLiteral string = PsiUtils.findFirstChildOfClassRecursive(packagesArg, StringLiteral.class);
    assertThat(string.getReferencedElement()).isEqualTo(buildFile1);
  }

  public void testLabelFragmentResolves() {
    BuildFile buildFile1 = createBuildFile(
      "java/com/google/tools/BUILD",
      "java_library(name = \"lib\")");

    BuildFile buildFile2 = createBuildFile(
      "java/com/google/other/BUILD",
      "java_library(name = \"lib2\", exports = [\"//java/com/google/tools:lib\"])");

    FuncallExpression libTarget = buildFile1.firstChildOfClass(FuncallExpression.class);
    assertThat(libTarget).isNotNull();

    Argument.Keyword packagesArg = buildFile2.firstChildOfClass(FuncallExpression.class).getArgList().getKeywordArgument("exports");
    StringLiteral string = PsiUtils.findFirstChildOfClassRecursive(packagesArg, StringLiteral.class);

    PsiReference[] references = string.getReferences();
    assertThat(references).hasLength(2);
    assertThat(references[0].resolve()).isEqualTo(libTarget);
    assertThat(references[1].resolve()).isEqualTo(buildFile1);
  }

}
