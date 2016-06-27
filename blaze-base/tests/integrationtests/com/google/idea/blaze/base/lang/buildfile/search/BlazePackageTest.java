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
package com.google.idea.blaze.base.lang.buildfile.search;

import com.google.idea.blaze.base.lang.buildfile.BuildFileIntegrationTestCase;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.intellij.psi.PsiFile;

import static com.google.common.truth.Truth.assertThat;

/**
 * Tests for BlazePackage
 */
public class BlazePackageTest extends BuildFileIntegrationTestCase {

  public void testFindPackage() {
    BuildFile packageFile = createBuildFile("java/com/google/BUILD");
    PsiFile subDirFile = createPsiFile("java/com/google/tools/test.txt");
    BlazePackage blazePackage = BlazePackage.getContainingPackage(subDirFile);
    assertThat(blazePackage).isNotNull();
    assertThat(blazePackage.buildFile).isEqualTo(packageFile);
  }

  public void testScopeDoesntCrossPackageBoundary() {
    BuildFile pkg = createBuildFile("java/com/google/BUILD");
    BuildFile subpkg = createBuildFile("java/com/google/other/BUILD");

    BlazePackage blazePackage = BlazePackage.getContainingPackage(pkg);
    assertThat(blazePackage.buildFile).isEqualTo(pkg);
    assertFalse(blazePackage.getSearchScope(false).contains(subpkg.getVirtualFile()));
  }

  public void testScopeIncludesSubdirectoriesWhichAreNotBlazePackages() {
    BuildFile pkg = createBuildFile("java/com/google/BUILD");
    BuildFile subpkg = createBuildFile("java/com/google/foo/bar/BUILD");
    PsiFile subDirFile = createPsiFile("java/com/google/foo/test.txt");

    BlazePackage blazePackage = BlazePackage.getContainingPackage(subDirFile);
    assertThat(blazePackage.buildFile).isEqualTo(pkg);
    assertTrue(blazePackage.getSearchScope(false).contains(subDirFile.getVirtualFile()));
  }

  public void testScopeLimitedToBlazeFiles() {
    BuildFile pkg = createBuildFile("java/com/google/BUILD");
    BuildFile subpkg = createBuildFile("java/com/google/foo/bar/BUILD");
    PsiFile subDirFile = createPsiFile("java/com/google/foo/test.txt");

    BlazePackage blazePackage = BlazePackage.getContainingPackage(subDirFile);
    assertThat(blazePackage.buildFile).isEqualTo(pkg);
    assertFalse(blazePackage.getSearchScope(true).contains(subDirFile.getVirtualFile()));
  }

}
