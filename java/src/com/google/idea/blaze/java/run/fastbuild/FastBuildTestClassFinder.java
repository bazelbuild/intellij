/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.java.run.fastbuild;

import static com.google.common.collect.Streams.stream;

import com.google.idea.blaze.base.io.VfsUtils;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.java.fastbuild.FastBuildBlazeData.JavaInfo;
import com.intellij.execution.ExecutionException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

final class FastBuildTestClassFinder {

  private final Project project;

  public FastBuildTestClassFinder(Project project) {
    this.project = project;
  }

  static FastBuildTestClassFinder getInstance(Project project) {
    return project.getService(FastBuildTestClassFinder.class);
  }

  String getTestClass(Label label, JavaInfo targetJavaInfo) throws ExecutionException {
    if (targetJavaInfo.testClass().isPresent()) {
      return targetJavaInfo.testClass().get();
    } else {

      PsiManager psiManager = PsiManager.getInstance(project);
      // If there's no 'test_class' attribute specified, we have to guess it. The class name is the
      // same as the Label name, but we have to try to figure out the package. Bazel does this in
      // JavaCommon.determinePrimaryClass.
      BlazeProjectData blazeProjectData =
          BlazeProjectDataManager.getInstance(project).getBlazeProjectData();

      Optional<String> testClass =
          determineTestClassFromSources(psiManager, blazeProjectData, label, targetJavaInfo);
      if (testClass.isPresent()) { // In Java9, we could chain these with Optional.or()
        return testClass.get();
      }

      return determineTestClassFromPackage(psiManager, blazeProjectData, label)
          .orElseThrow(() -> new ExecutionException("Couldn't determine test class."));
    }
  }

  // This is the first part of Bazel's JavaCommon.determinePrimaryClass()
  private static Optional<String> determineTestClassFromSources(
      PsiManager psiManager,
      BlazeProjectData blazeProjectData,
      Label label,
      JavaInfo targetJavaInfo) {

    String targetName = label.targetName().toString();
    List<File> targetSources =
        blazeProjectData.getArtifactLocationDecoder().decodeAll(targetJavaInfo.sources());
    return targetSources.stream()
        .flatMap(source -> stream(getMatchingClassName(psiManager, targetName, source)))
        .findFirst();
  }

  // If determineTestClassFromSources doesn't work, Bazel tries to guess the package name with some
  // complicated logic based on directory names (see JavaUtil.getJavaFullClassName()). Rather than
  // copy/pasting that logic, we'll just read the class name from a source file with the appropriate
  // name. It won't always be the same as what Bazel comes up with, but it should be close enough.
  private static Optional<String> determineTestClassFromPackage(
      PsiManager psiManager, BlazeProjectData blazeProjectData, Label label) {

    File file =
        new File(
            blazeProjectData.getWorkspacePathResolver().resolveToFile(label.blazePackage()),
            label.targetName() + ".java");
    return getMatchingClassName(psiManager, label.targetName().toString(), file);
  }

  private static Optional<String> getMatchingClassName(
      PsiManager psiManager, String className, File file) {
    VirtualFile virtualFile = VfsUtils.resolveVirtualFile(file, /* refreshIfNeeded= */ true);
    if (virtualFile == null) {
      return Optional.empty();
    }
    PsiFile psiFile = psiManager.findFile(virtualFile);
    if (!(psiFile instanceof PsiJavaFile)) {
      return Optional.empty();
    }
    return Arrays.stream(psiFile.getChildren())
        .filter(PsiClass.class::isInstance)
        .map(PsiClass.class::cast)
        .filter(c -> className.equals(c.getName()) && c.getQualifiedName() != null)
        .map(PsiClass::getQualifiedName)
        .findAny();
  }
}
