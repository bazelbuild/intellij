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

import com.google.idea.blaze.base.lang.buildfile.BuildFileIntegrationTestCase;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.copy.CopyHandler;

/**
 * Tests copying files
 */
public class FileCopyTest extends BuildFileIntegrationTestCase {

  public void testCopyingJavaFileReferencedByGlob() {
    createDirectory("java");
    PsiFile javaFile = createPsiFile(
      "java/Test.java",
      "package java;",
      "public class Test {}");

    PsiFile javaFile2 = createPsiFile(
      "java/Test2.java",
      "package java;",
      "public class Test2 {}");

    createBuildFile(
      "java/BUILD",
      "java_library(",
      "    name = 'lib',",
      "    srcs = glob(['**/*.java']),",
      ")");

    PsiDirectory otherDir = createPsiDirectory("java/other");

    WriteCommandAction.runWriteCommandAction(null, () -> {
      CopyHandler.doCopy(new PsiElement[] {javaFile, javaFile2}, otherDir);
    });
  }
}
