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
package com.google.idea.blaze.java.lang.build;

import com.google.idea.blaze.base.lang.buildfile.BuildFileIntegrationTestCase;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.lang.buildfile.psi.util.PsiUtils;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.safeDelete.SafeDeleteHandler;

/** Tests for the safe delete action which aren't covered by existing tests. */
public class SafeDeleteTest extends BuildFileIntegrationTestCase {

  public void testIndirectGlobReferencesNotIncluded() {
    PsiFile javaFile =
        createPsiFile("com/google/Test.java", "package com.google;", "public class Test {}");

    PsiClass javaClass = PsiUtils.findFirstChildOfClassRecursive(javaFile, PsiClass.class);

    BuildFile buildFile =
        createBuildFile(
            "com/google/BUILD",
            "java_library(",
            "    name = 'lib'",
            "    srcs = glob(['*.java'])",
            ")");

    try {
      SafeDeleteHandler.invoke(getProject(), new PsiElement[] {javaClass}, true);
    } catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      fail("Glob reference was incorrectly included");
      return;
    }
  }

  public void testDirectGlobReferencesIncluded() {
    PsiFile javaFile =
        createPsiFile("com/google/Test.java", "package com.google;", "public class Test {}");

    PsiClass javaClass = PsiUtils.findFirstChildOfClassRecursive(javaFile, PsiClass.class);

    BuildFile buildFile =
        createBuildFile(
            "com/google/BUILD",
            "java_library(",
            "    name = 'lib'",
            "    srcs = glob(['Test.java'])",
            ")");

    try {
      SafeDeleteHandler.invoke(getProject(), new PsiElement[] {javaClass}, true);
    } catch (BaseRefactoringProcessor.ConflictsInTestsException expected) {
      return;
    }
    fail("Expected an unsafe usage to be found");
  }
}
