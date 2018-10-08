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

import static com.google.common.base.Preconditions.checkState;
import static java.util.stream.Collectors.joining;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Streams;
import com.google.idea.blaze.base.io.VfsUtils;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.java.fastbuild.FastBuildBlazeData;
import com.google.idea.blaze.java.fastbuild.FastBuildBlazeData.AndroidInfo;
import com.google.idea.blaze.java.fastbuild.FastBuildBlazeData.JavaInfo;
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
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;

final class FastBuildTestEnvironmentCreator {

  private static final String OUTPUT_FILE_VARIABLE = "XML_OUTPUT_FILE";
  private static final String RUNFILES_DIR_VARIABLE = "TEST_SRCDIR";
  private static final String TARGET_VARIABLE = "TEST_TARGET";
  private static final String TEMP_DIRECTORY_VARIABLE = "TEST_TMPDIR";
  private static final String TEST_FILTER_VARIABLE = "TESTBRIDGE_TEST_ONLY";
  private static final String WORKSPACE_VARIABLE = "TEST_WORKSPACE";

  private final Project project;
  private final String testClassProperty;
  private final String testRunner;
  private final RobolectricDepsPropertiesFinder robolectricDepsPropertiesFinder;

  FastBuildTestEnvironmentCreator(
      Project project,
      String testClassProperty,
      String testRunner,
      RobolectricDepsPropertiesFinder robolectricDepsPropertiesFinder) {
    this.project = project;
    this.testClassProperty = testClassProperty;
    this.testRunner = testRunner;
    this.robolectricDepsPropertiesFinder = robolectricDepsPropertiesFinder;
  }

  GeneralCommandLine createCommandLine(
      Kind kind,
      FastBuildInfo fastBuildInfo,
      File outputFile,
      @Nullable String testFilter,
      int debugPort)
      throws ExecutionException {

    FastBuildBlazeData targetData = fastBuildInfo.blazeData().get(fastBuildInfo.label());
    checkState(targetData != null, "Couldn't find blaze data for %s", fastBuildInfo.label());
    checkState(
        targetData.javaInfo().isPresent(), "Couldn't find Java info for %s", fastBuildInfo.label());
    JavaInfo targetJavaInfo = targetData.javaInfo().get();

    // To emulate 'blaze test', the binary should be launched from something like
    // blaze-out/k8-opt/bin/path/to/package/MyLabel.runfiles/io_bazel
    String workspaceName = targetData.workspaceName();
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

    targetJavaInfo.jvmFlags().forEach(commandLine::addParameter);

    commandLine.withParameters(
        "-cp",
        createClasspath(fastBuildInfo),
        getTestSuiteParameter(fastBuildInfo.label(), targetJavaInfo));

    if (kind.equals(Kind.ANDROID_LOCAL_TEST)) {
      addAndroidLocalTestParameters(commandLine, fastBuildInfo);
    }

    commandLine.withParameters(testRunner);

    if (kind.equals(Kind.ANDROID_ROBOLECTRIC_TEST)) {
      addAndroidRobolectricTestParameters(commandLine, fastBuildInfo, workingDir);
    }

    commandLine
        .withEnvironment(OUTPUT_FILE_VARIABLE, outputFile.getAbsolutePath())
        .withEnvironment(RUNFILES_DIR_VARIABLE, runfilesDir.toString())
        .withEnvironment(TARGET_VARIABLE, fastBuildInfo.label().toString())
        .withEnvironment(WORKSPACE_VARIABLE, workspaceName);

    String tmpdir = System.getProperty("java.io.tmpdir");
    if (tmpdir != null) {
      commandLine.withEnvironment(TEMP_DIRECTORY_VARIABLE, tmpdir);
    }

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

  private String getTestSuiteParameter(Label label, JavaInfo targetJavaInfo)
      throws ExecutionException {
    return "-D" + testClassProperty + "=" + getTestClass(label, targetJavaInfo);
  }

  private String getTestClass(Label label, JavaInfo targetJavaInfo) throws ExecutionException {
    if (targetJavaInfo.testClass().isPresent()) {
      return targetJavaInfo.testClass().get();
    } else {
      return determineTestClassFromSources(label, targetJavaInfo);
    }
  }

  private String determineTestClassFromSources(Label label, JavaInfo targetJavaInfo)
      throws ExecutionException {

    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();

    String targetName = label.targetName().toString();
    PsiManager psiManager = PsiManager.getInstance(project);
    for (File source :
        blazeProjectData.getArtifactLocationDecoder().decodeAll(targetJavaInfo.sources())) {
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

  private void addAndroidRobolectricTestParameters(
      GeneralCommandLine commandLine, FastBuildInfo fastBuildInfo, Path workingDir) {

    ImmutableMap<Label, FastBuildBlazeData> blazeData = fastBuildInfo.blazeData();
    FastBuildBlazeData testBlazeData = blazeData.get(fastBuildInfo.label());

    // First, any libraries that are direct dependencies of the test target go into
    // --strict_libraries
    String strictLibraries =
        testBlazeData.dependencies().stream()
            .filter(blazeData::containsKey)
            .map(blazeData::get)
            .flatMap(d -> Streams.stream(d.androidInfo()))
            .flatMap(ai -> Streams.stream(getAndroidResource(ai, workingDir)))
            .collect(joining(","));
    if (!strictLibraries.isEmpty()) {
      commandLine.withParameters("--strict_libraries", strictLibraries);
    }

    // Now, add the entire list of libraries to --android_libraries. (Those in --strict_libraries
    // will be duplicated here, but that's okay; Blaze does the same thing.)
    Set<String> libraries = new HashSet<>();
    Set<Label> processedLabels = new HashSet<>();
    recursivelyAddAndroidLibraries(
        fastBuildInfo.label(), blazeData, libraries, processedLabels, workingDir);
    if (!libraries.isEmpty()) {
      commandLine.withParameters("--android_libraries", libraries.stream().collect(joining(",")));
    }
  }

  private void addAndroidLocalTestParameters(
      GeneralCommandLine commandLine, FastBuildInfo fastBuildInfo) throws ExecutionException {
    commandLine
        .withParameters("-Drobolectric.offline=true")
        .withParameters(
            "-Drobolectric-deps.properties="
                + robolectricDepsPropertiesFinder.getPropertiesLocation(fastBuildInfo))
        .withParameters("-Duse_framework_manifest_parser=true")
        .withParameters(
            "-Dorg.robolectric.packagesToNotAcquire=com.google.testing.junit.runner.util");
  }

  private void recursivelyAddAndroidLibraries(
      Label label,
      Map<Label, FastBuildBlazeData> blazeData,
      Set<String> libraries,
      Set<Label> processedLabels,
      Path workingDir) {
    processedLabels.add(label);
    if (!blazeData.containsKey(label)) {
      return;
    }
    FastBuildBlazeData currentData = blazeData.get(label);
    if (currentData.androidInfo().isPresent()) {
      getAndroidResource(currentData.androidInfo().get(), workingDir).ifPresent(libraries::add);
    }
    currentData
        .dependencies()
        .stream()
        .filter(d -> !processedLabels.contains(d))
        .forEach(
            d ->
                recursivelyAddAndroidLibraries(
                    d, blazeData, libraries, processedLabels, workingDir));
  }

  // Returns a string like
  //   java/com/google/android/lib/AndroidManifest.xml:java/com/google/android/lib/lib.aar
  private Optional<String> getAndroidResource(AndroidInfo androidInfo, Path workingDir) {
    if (!androidInfo.mergedManifest().isPresent() || !androidInfo.aar().isPresent()) {
      return Optional.empty();
    }
    // Very occasionally (<1% of the time in my tests) these files won't exist. I'm not really sure
    // why that is. There must be something telling Blaze that they aren't needed for the build even
    // though it's listed as a dependency.
    if (!workingDir
        .resolve(androidInfo.mergedManifest().get().getRelativePath())
        .toFile()
        .exists()) {
      return Optional.empty();
    }
    return Optional.of(
        androidInfo.mergedManifest().get().getRelativePath()
            + ":"
            + androidInfo.aar().get().getRelativePath());
  }
}
