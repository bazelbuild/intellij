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

import static com.google.common.truth.Truth.assertThat;

import com.google.idea.blaze.base.lang.buildfile.BuildFileIntegrationTestCase;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.lang.buildfile.psi.GlobExpression;
import com.google.idea.blaze.base.lang.buildfile.psi.util.PsiUtils;
import com.google.idea.blaze.base.lang.buildfile.search.FindUsages;
import com.google.idea.blaze.base.lang.projectview.language.ProjectViewLanguage;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.file.impl.FileManager;
import com.intellij.testFramework.LightVirtualFile;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests that file references in globs are included in the 'find usages' results. */
@RunWith(JUnit4.class)
public class GlobFindUsagesTest extends BuildFileIntegrationTestCase {

  @Test
  public void testSimpleGlobReferencingSingleFile() {
    PsiFile ref = createPsiFile("java/com/google/Test.java");
    createBuildFile("java/com/google/BUILD", "glob(['**/*.java'])");

    PsiReference[] references = FindUsages.findAllReferences(ref);
    assertThat(references).hasLength(1);
    assertThat(references[0].getElement()).isInstanceOf(GlobExpression.class);
  }

  @Test
  public void testSimpleGlobReferencingSingleFile2() {
    PsiFile ref = createPsiFile("java/com/google/Test.java");
    BuildFile file = createBuildFile("java/com/google/BUILD", "glob(['*.java'])");

    GlobExpression glob = PsiUtils.findFirstChildOfClassRecursive(file, GlobExpression.class);

    PsiReference[] references = FindUsages.findAllReferences(ref);
    assertThat(references).hasLength(1);
    assertThat(references[0].getElement()).isEqualTo(glob);
  }

  @Test
  public void testSimpleGlobReferencingSingleFile3() {
    PsiFile ref = createPsiFile("java/com/google/Test.java");
    BuildFile file = createBuildFile("java/com/google/BUILD", "glob(['T*t.java'])");

    GlobExpression glob = PsiUtils.findFirstChildOfClassRecursive(file, GlobExpression.class);

    PsiReference[] references = FindUsages.findAllReferences(ref);
    assertThat(references).hasLength(1);
    assertThat(references[0].getElement()).isEqualTo(glob);
  }

  @Test
  public void testGlobReferencingMultipleFiles() {
    PsiFile ref1 = createPsiFile("java/com/google/Test.java");
    PsiFile ref2 = createPsiFile("java/com/google/Foo.java");
    BuildFile file = createBuildFile("java/com/google/BUILD", "glob(['*.java'])");

    GlobExpression glob = PsiUtils.findFirstChildOfClassRecursive(file, GlobExpression.class);

    PsiReference[] references = FindUsages.findAllReferences(ref1);
    assertThat(references).hasLength(1);
    assertThat(references[0].getElement()).isEqualTo(glob);

    references = FindUsages.findAllReferences(ref2);
    assertThat(references).hasLength(1);
    assertThat(references[0].getElement()).isEqualTo(glob);
  }

  @Test
  public void testFindsSubDirectories() {
    PsiFile ref1 = createPsiFile("java/com/google/test/Test.java");
    BuildFile file = createBuildFile("java/com/google/BUILD", "glob(['**/*.java'])");

    GlobExpression glob = PsiUtils.findFirstChildOfClassRecursive(file, GlobExpression.class);

    PsiReference[] references = FindUsages.findAllReferences(ref1);
    assertThat(references).hasLength(1);
    assertThat(references[0].getElement()).isEqualTo(glob);
  }

  @Test
  public void testGlobWithExcludes() {
    PsiFile test = createPsiFile("java/com/google/tests/Test.java");
    PsiFile foo = createPsiFile("java/com/google/Foo.java");
    BuildFile file =
        createBuildFile(
            "java/com/google/BUILD",
            "glob(" + "  ['**/*.java']," + "  exclude = ['tests/*.java'])");

    GlobExpression glob = PsiUtils.findFirstChildOfClassRecursive(file, GlobExpression.class);

    PsiReference[] references = FindUsages.findAllReferences(foo);
    assertThat(references).hasLength(1);
    assertThat(references[0].getElement()).isEqualTo(glob);

    assertThat(FindUsages.findAllReferences(test)).isEmpty();
  }

  @Test
  public void testIncludeDirectories() {
    PsiDirectory dir = createPsiDirectory("java/com/google/tests");
    createPsiFile("java/com/google/tests/Test.java");
    createPsiFile("java/com/google/Foo.java");
    BuildFile file =
        createBuildFile(
            "java/com/google/BUILD",
            "glob(" + "  ['**/*']," + "  exclude = ['BUILD']," + "  exclude_directories = 0)");

    GlobExpression glob = PsiUtils.findFirstChildOfClassRecursive(file, GlobExpression.class);

    PsiReference[] references = FindUsages.findAllReferences(dir);
    assertThat(references).hasLength(1);
    assertThat(references[0].getElement()).isEqualTo(glob);
  }

  @Test
  public void testExcludeDirectories() {
    PsiDirectory dir = createPsiDirectory("java/com/google/tests");
    createPsiFile("java/com/google/tests/Test.java");
    createPsiFile("java/com/google/Foo.java");
    BuildFile file =
        createBuildFile(
            "java/com/google/BUILD", "glob(" + "  ['**/*']," + "  exclude = ['BUILD'])");

    PsiUtils.findFirstChildOfClassRecursive(file, GlobExpression.class);

    PsiReference[] references = FindUsages.findAllReferences(dir);
    assertThat(references).isEmpty();
  }

  @Test
  public void testFilesInSubpackagesExcluded() {
    BuildFile pkg = createBuildFile("java/com/google/BUILD", "glob(['**/*.java'])");
    BuildFile subPkg = createBuildFile("java/com/google/other/BUILD");
    createFile("java/com/google/other/Other.java");

    PsiUtils.findFirstChildOfClassRecursive(pkg, GlobExpression.class);

    PsiReference[] references = FindUsages.findAllReferences(subPkg);
    assertThat(references).isEmpty();
  }

  // regression test for b/29267289
  @Test
  public void testInMemoryFileHandledGracefully() {
    createBuildFile("java/com/google/BUILD", "glob(['**/*.java'])");

    LightVirtualFile inMemoryFile =
        new LightVirtualFile("mockProjectViewFile", ProjectViewLanguage.INSTANCE, "");

    FileManager fileManager =
        ((PsiManagerEx) PsiManager.getInstance(getProject())).getFileManager();
    fileManager.setViewProvider(
        inMemoryFile, fileManager.createFileViewProvider(inMemoryFile, true));

    PsiFile psiFile = fileManager.findFile(inMemoryFile);

    FindUsages.findAllReferences(psiFile);
  }
}
