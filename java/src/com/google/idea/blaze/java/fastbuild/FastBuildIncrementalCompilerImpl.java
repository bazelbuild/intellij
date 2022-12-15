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
package com.google.idea.blaze.java.fastbuild;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.StatusOutput;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.common.PrintOutput;
import com.google.idea.blaze.java.fastbuild.FastBuildBlazeData.JavaInfo;
import com.google.idea.blaze.java.fastbuild.FastBuildCompiler.CompileInstructions;
import com.google.idea.blaze.java.fastbuild.FastBuildLogDataScope.FastBuildLogOutput;
import com.google.idea.blaze.java.fastbuild.FastBuildState.BuildOutput;
import com.google.idea.common.util.ConcurrencyUtil;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

final class FastBuildIncrementalCompilerImpl implements FastBuildIncrementalCompiler {

  private final BlazeProjectDataManager projectDataManager;
  private final FastBuildCompilerFactory compilerFactory;

  FastBuildIncrementalCompilerImpl(Project project) {
    this.projectDataManager = BlazeProjectDataManager.getInstance(project);
    this.compilerFactory = FastBuildCompilerFactory.getInstance(project);
  }

  @Override
  public ListenableFuture<BuildOutput> compile(
      BlazeContext context, Label label, FastBuildState buildState, Set<File> modifiedFiles) {
    checkState(buildState.completedBuildOutput().isPresent());
    BuildOutput buildOutput = buildState.completedBuildOutput().get();
    checkState(buildOutput.blazeData().containsKey(label));

    return ConcurrencyUtil.getAppExecutorService()
        .submit(
            () -> {
              ChangedSourceInfo changedSourceInfo =
                  getPathsToCompile(context, label, buildOutput.blazeData(), modifiedFiles);

              if (!changedSourceInfo.pathsToCompile.isEmpty()) {
                CompileInstructions instructions =
                    CompileInstructions.builder()
                        .outputDirectory(buildState.compilerOutputDirectory())
                        .classpath(ImmutableList.of(buildOutput.deployJar()))
                        .filesToCompile(changedSourceInfo.pathsToCompile)
                        .annotationProcessorClassNames(
                            changedSourceInfo.annotationProcessorClassNames)
                        .annotationProcessorClasspath(
                            changedSourceInfo.annotationProcessorClasspath)
                        .build();

                for (FastBuildCompilationModification modification :
                    FastBuildCompilationModification.EP_NAME.getExtensions()) {
                  instructions = modification.modifyInstructions(instructions);
                }

                compilerFactory
                    .getCompilerFor(label, buildOutput.blazeData())
                    .compile(context, instructions);
              } else {
                context.output(new PrintOutput("No modified files to compile."));
              }
              return buildOutput;
            });
  }

  private ChangedSourceInfo getPathsToCompile(
      BlazeContext context,
      Label label,
      Map<Label, FastBuildBlazeData> blazeData,
      Set<File> modifiedSinceBuild) {

    Stopwatch timer = Stopwatch.createStarted();

    BlazeProjectData projectData = projectDataManager.getBlazeProjectData();
    Set<File> sourceFiles = new HashSet<>();
    Set<String> annotationProcessorClassNames = new HashSet<>();
    // Use ImmutableSet.Builder because it will preserve the classpath order.
    ImmutableSet.Builder<File> annotationProcessorsClasspath = ImmutableSet.builder();
    Set<Label> seenTargets = new HashSet<>();
    AtomicInteger affectedTargets = new AtomicInteger(0);
    recursivelyAddModifiedJavaSources(
        projectData.getArtifactLocationDecoder(),
        blazeData,
        label,
        seenTargets,
        sourceFiles,
        annotationProcessorClassNames,
        annotationProcessorsClasspath,
        modifiedSinceBuild,
        affectedTargets);

    context.output(new StatusOutput("Calculated compilation paths in " + timer));
    context.output(FastBuildLogOutput.milliseconds("calculate_changed_sources_time_ms", timer));
    context.output(FastBuildLogOutput.keyValue("affected_targets", affectedTargets.toString()));

    return new ChangedSourceInfo(
        sourceFiles, annotationProcessorClassNames, annotationProcessorsClasspath.build());
  }

  private void recursivelyAddModifiedJavaSources(
      ArtifactLocationDecoder artifactLocationDecoder,
      Map<Label, FastBuildBlazeData> blazeData,
      Label label,
      Set<Label> seenTargets,
      Set<File> sourceFiles,
      Set<String> annotationProcessorClassNames,
      ImmutableSet.Builder<File> annotationProcessorsClasspath,
      Set<File> modifiedSinceBuild,
      AtomicInteger affectedTargets) {
    if (seenTargets.contains(label)) {
      return;
    }

    seenTargets.add(label);

    FastBuildBlazeData targetIdeInfo = blazeData.get(label);
    if (targetIdeInfo == null || !targetIdeInfo.javaInfo().isPresent()) {
      return;
    }

    JavaInfo javaInfo = targetIdeInfo.javaInfo().get();

    boolean addedSources = false;
    for (ArtifactLocation sourceArtifact : javaInfo.sources()) {
      File sourceFile = artifactLocationDecoder.decode(sourceArtifact);
      if (sourceFile.getName().endsWith(".java")
          && modifiedSinceBuild.contains(sourceFile)
          && sourceFile.exists()) {
        sourceFiles.add(sourceFile);
        addedSources = true;
      }
    }

    if (addedSources) {
      affectedTargets.incrementAndGet();
      annotationProcessorClassNames.addAll(javaInfo.annotationProcessorClassNames());
      for (ArtifactLocation artifactLocation : javaInfo.annotationProcessorClasspath()) {
        annotationProcessorsClasspath.add(artifactLocationDecoder.decode(artifactLocation));
      }
    }

    targetIdeInfo
        .dependencies()
        .forEach(
            dep ->
                recursivelyAddModifiedJavaSources(
                    artifactLocationDecoder,
                    blazeData,
                    dep,
                    seenTargets,
                    sourceFiles,
                    annotationProcessorClassNames,
                    annotationProcessorsClasspath,
                    modifiedSinceBuild,
                    affectedTargets));
  }

  private static class ChangedSourceInfo {
    final Set<File> pathsToCompile;
    final Set<String> annotationProcessorClassNames;
    final Set<File> annotationProcessorClasspath;

    private ChangedSourceInfo(
        Set<File> pathsToCompile,
        Set<String> annotationProcessorClassNames,
        Set<File> annotationProcessorClasspath) {
      this.pathsToCompile = pathsToCompile;
      this.annotationProcessorClassNames = annotationProcessorClassNames;
      this.annotationProcessorClasspath = annotationProcessorClasspath;
    }
  }
}
