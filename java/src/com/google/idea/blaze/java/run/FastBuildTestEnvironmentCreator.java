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
package com.google.idea.blaze.java.run;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Joiner;
import com.google.idea.blaze.base.io.VfsUtils;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.java.fastbuild.FastBuildBlazeData;
import com.google.idea.blaze.java.fastbuild.FastBuildBlazeData.ProviderInfo.Type;
import com.google.idea.blaze.java.fastbuild.FastBuildInfo;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.annotation.Nullable;

final class FastBuildTestEnvironmentCreator {

  private static final String OUTPUT_FILE_VARIABLE = "XML_OUTPUT_FILE";
  private static final String RUNFILES_DIR_VARIABLE = "TEST_SRCDIR";
  private static final String TARGET_VARIABLE = "TEST_TARGET";
  private static final String TEST_FILTER_VARIABLE = "TESTBRIDGE_TEST_ONLY";
  private static final String WORKSPACE_VARIABLE = "TEST_WORKSPACE";

  private final Project project;
  private final String testClassProperty;
  private final String testRunner;

  FastBuildTestEnvironmentCreator(
      Project project, String testClassProperty, String testRunner) {
    this.project = project;
    this.testClassProperty = testClassProperty;
    this.testRunner = testRunner;
  }

  GeneralCommandLine createCommandLine(
      FastBuildInfo fastBuildInfo, File outputFile, @Nullable String testFilter, int debugPort)
      throws ExecutionException {

    // To emulate 'blaze test', the binary should be launched from something like
    // blaze-out/k8-opt/bin/path/to/package/MyLabel.runfiles/io_bazel
    String workspaceName = fastBuildInfo.blazeData().get(fastBuildInfo.label()).workspaceName();
    Path runfilesDir =
        Paths.get(
            fastBuildInfo.deployJar().getParent(),
            fastBuildInfo.label().targetName() + ".runfiles");
    Path workingDir = runfilesDir.resolve(workspaceName);

    GeneralCommandLine commandLine =
        new GeneralCommandLine(getJavaBinPath()).withWorkDirectory(workingDir.toString());

    if (debugPort > 0) {
      commandLine.withParameters(
          "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=" + debugPort);
    }

    commandLine.withParameters(
        "-cp", createClasspath(fastBuildInfo), getTestSuiteParameter(fastBuildInfo), testRunner);

    commandLine
        .withEnvironment(OUTPUT_FILE_VARIABLE, outputFile.getAbsolutePath())
        .withEnvironment(RUNFILES_DIR_VARIABLE, runfilesDir.toString())
        .withEnvironment(TARGET_VARIABLE, fastBuildInfo.label().toString())
        .withEnvironment(WORKSPACE_VARIABLE, workspaceName);

    if (testFilter != null) {
      commandLine.withEnvironment(TEST_FILTER_VARIABLE, testFilter);
    }

    return commandLine;
  }

  private String getJavaBinPath() throws ExecutionException {
    Sdk projectSdk = ProjectRootManager.getInstance(project).getProjectSdk();
    if (projectSdk == null) {
      throw new ExecutionException("No project SDK is configured.");
    }
    if (!(projectSdk.getSdkType() instanceof JavaSdkType)) {
      throw new ExecutionException("Project SDK isn't a Java SDK.");
    }
    return ((JavaSdkType) projectSdk.getSdkType()).getVMExecutablePath(projectSdk);
  }

  private String createClasspath(FastBuildInfo fastBuildInfo) {
    return Joiner.on(':').join(fastBuildInfo.classpath());
  }

  private String getTestSuiteParameter(FastBuildInfo fastBuildInfo) throws ExecutionException {
    return "-D" + testClassProperty + "=" + getTestClass(fastBuildInfo);
  }

  private String getTestClass(FastBuildInfo fastBuildInfo) throws ExecutionException {
    FastBuildBlazeData targetData = fastBuildInfo.blazeData().get(fastBuildInfo.label());
    checkState(targetData.providerInfo().type().equals(Type.JAVA_INFO));
    if (targetData.providerInfo().javaInfo().testClass().isPresent()) {
      return targetData.providerInfo().javaInfo().testClass().get();
    } else {
      return determineTestClassFromSources(fastBuildInfo.label(), targetData);
    }
  }

  private String determineTestClassFromSources(Label label, FastBuildBlazeData targetData)
      throws ExecutionException {

    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();

    String targetName = label.targetName().toString();
    PsiManager psiManager = PsiManager.getInstance(project);
    for (File source :
        blazeProjectData.artifactLocationDecoder.decodeAll(
            targetData.providerInfo().javaInfo().sources())) {
      VirtualFile virtualFile = VfsUtils.resolveVirtualFile(source);
      if (virtualFile == null) {
        continue;
      }
      PsiFile psiFile = psiManager.findFile(virtualFile);
      if (!(psiFile instanceof PsiJavaFile)) {
        continue;
      }
      for (PsiElement psiElement : psiFile.getChildren()) {
        if (!(psiElement instanceof PsiClass)) {
          continue;
        }
        PsiClass psiClass = (PsiClass) psiElement;
        if (targetName.equals(psiClass.getName()) && psiClass.getQualifiedName() != null) {
          return ((PsiClass) psiElement).getQualifiedName();
        }
      }
    }
    throw new ExecutionException("Couldn't determine test class");
  }
}
