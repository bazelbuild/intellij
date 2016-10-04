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

import static com.google.common.truth.Truth.assertThat;

import com.google.idea.blaze.base.lang.buildfile.BuildFileIntegrationTestCase;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.lang.buildfile.psi.GlobExpression;
import com.google.idea.blaze.base.lang.buildfile.psi.util.PsiUtils;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.ResolveResult;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/** Tests that glob references are resolved correctly. */
public class GlobReferenceTest extends BuildFileIntegrationTestCase {

  public void testSimpleGlobReferencingSingleFile() {
    PsiFile ref = createPsiFile("java/com/google/Test.java");
    BuildFile file = createBuildFile("java/com/google/BUILD", "glob(['**/*.java'])");

    GlobExpression glob = PsiUtils.findFirstChildOfClassRecursive(file, GlobExpression.class);
    List<PsiElement> references = multiResolve(glob);
    assertThat(references).hasSize(1);
    assertThat(references).containsExactly(ref);
  }

  public void testSimpleGlobReferencingSingleFile2() {
    PsiFile ref = createPsiFile("java/com/google/Test.java");
    BuildFile file = createBuildFile("java/com/google/BUILD", "glob(['*.java'])");

    GlobExpression glob = PsiUtils.findFirstChildOfClassRecursive(file, GlobExpression.class);
    List<PsiElement> references = multiResolve(glob);
    assertThat(references).hasSize(1);
    assertThat(references).containsExactly(ref);
  }

  public void testSimpleGlobReferencingSingleFile3() {
    PsiFile ref = createPsiFile("java/com/google/Test.java");
    BuildFile file = createBuildFile("java/com/google/BUILD", "glob(['T*t.java'])");

    GlobExpression glob = PsiUtils.findFirstChildOfClassRecursive(file, GlobExpression.class);
    List<PsiElement> references = multiResolve(glob);
    assertThat(references).hasSize(1);
    assertThat(references).containsExactly(ref);
  }

  public void testGlobReferencingMultipleFiles() {
    PsiFile ref1 = createPsiFile("java/com/google/Test.java");
    PsiFile ref2 = createPsiFile("java/com/google/Foo.java");
    BuildFile file = createBuildFile("java/com/google/BUILD", "glob(['*.java'])");

    GlobExpression glob = PsiUtils.findFirstChildOfClassRecursive(file, GlobExpression.class);
    List<PsiElement> references = multiResolve(glob);
    assertThat(references).hasSize(2);
    assertThat(references).containsExactly(ref1, ref2);
  }

  public void testFindsSubDirectories() {
    PsiFile ref1 = createPsiFile("java/com/google/test/Test.java");
    PsiFile ref2 = createPsiFile("java/com/google/Foo.java");
    BuildFile file = createBuildFile("java/com/google/BUILD", "glob(['**/*.java'])");

    GlobExpression glob = PsiUtils.findFirstChildOfClassRecursive(file, GlobExpression.class);
    List<PsiElement> references = multiResolve(glob);
    assertThat(references).hasSize(2);
    assertThat(references).containsExactly(ref1, ref2);
  }

  public void testGlobWithExcludes() {
    PsiFile test = createPsiFile("java/com/google/tests/Test.java");
    PsiFile foo = createPsiFile("java/com/google/Foo.java");
    BuildFile file =
        createBuildFile(
            "java/com/google/BUILD",
            "glob(" + "  ['**/*.java']," + "  exclude = ['tests/*.java'])");

    GlobExpression glob = PsiUtils.findFirstChildOfClassRecursive(file, GlobExpression.class);
    List<PsiElement> references = multiResolve(glob);
    assertThat(references).hasSize(1);
    assertThat(references).containsExactly(foo);
  }

  public void testIncludeDirectories() {
    createDirectory("java/com/google/tests");
    PsiFile test = createPsiFile("java/com/google/tests/Test.java");
    PsiFile foo = createPsiFile("java/com/google/Foo.java");
    BuildFile file =
        createBuildFile(
            "java/com/google/BUILD",
            "glob(" + "  ['**/*']," + "  exclude = ['BUILD']," + "  exclude_directories = 0)");

    GlobExpression glob = PsiUtils.findFirstChildOfClassRecursive(file, GlobExpression.class);
    List<PsiElement> references = multiResolve(glob);
    assertThat(references).hasSize(3);
    assertThat(references).containsExactly(foo, test, test.getParent());
  }

  public void testExcludeDirectories() {
    createDirectory("java/com/google/tests");
    PsiFile test = createPsiFile("java/com/google/tests/Test.java");
    PsiFile foo = createPsiFile("java/com/google/Foo.java");
    BuildFile file =
        createBuildFile(
            "java/com/google/BUILD", "glob(" + "  ['**/*']," + "  exclude = ['BUILD'])");

    GlobExpression glob = PsiUtils.findFirstChildOfClassRecursive(file, GlobExpression.class);
    List<PsiElement> references = multiResolve(glob);
    assertThat(references).hasSize(2);
    assertThat(references).containsExactly(foo, test);
  }

  public void testFilesInSubpackagesExcluded() {
    BuildFile pkg = createBuildFile("java/com/google/BUILD", "glob(['**/*.java'])");
    BuildFile subPkg = createBuildFile("java/com/google/other/BUILD");
    createFile("java/com/google/other/Other.java");

    GlobExpression glob = PsiUtils.findFirstChildOfClassRecursive(pkg, GlobExpression.class);
    List<PsiElement> references = multiResolve(glob);
    assertThat(references).isEmpty();
  }

  private List<PsiElement> multiResolve(GlobExpression glob) {
    ResolveResult[] result = glob.getReference().multiResolve(false);
    return Arrays.stream(result)
        .map(ResolveResult::getElement)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }
}
