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
import com.google.idea.blaze.base.console.BlazeConsoleService;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.java.fastbuild.FastBuildBlazeData.JavaInfo;
import com.google.idea.blaze.java.fastbuild.FastBuildCompiler.CompileInstructions;
import com.google.idea.blaze.java.fastbuild.FastBuildState.BuildOutput;
import com.google.idea.common.concurrency.ConcurrencyUtil;
import com.intellij.execution.ui.ConsoleViewContentType;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

final class FastBuildIncrementalCompilerImpl implements FastBuildIncrementalCompiler {

  private final BlazeProjectDataManager projectDataManager;
  private final FastBuildCompilerFactory compilerFactory;
  private final BlazeConsoleService blazeConsoleService;

  FastBuildIncrementalCompilerImpl(
      BlazeProjectDataManager projectDataManager,
      FastBuildCompilerFactory compilerFactory,
      BlazeConsoleService blazeConsoleService) {
    this.projectDataManager = projectDataManager;
    this.compilerFactory = compilerFactory;
    this.blazeConsoleService = blazeConsoleService;
  }

  @Override
  public ListenableFuture<BuildOutput> compile(
      Label label, FastBuildState buildState, Map<String, String> loggingData) {
    checkState(buildState.completedBuildOutput().isPresent());
    BuildOutput buildOutput = buildState.completedBuildOutput().get();
    checkState(buildOutput.blazeData().containsKey(label));

    return ConcurrencyUtil.getAppExecutorService()
        .submit(
            () -> {
              BlazeConsoleWriter writer = new BlazeConsoleWriter(blazeConsoleService);

              ChangedSourceInfo changedSourceInfo =
                  getPathsToCompile(
                      label,
                      buildOutput.blazeData(),
                      buildState.modifiedFiles(),
                      writer,
                      loggingData);

              if (!changedSourceInfo.pathsToCompile.isEmpty()) {
                compilerFactory
                    .getCompilerFor(label, buildOutput.blazeData())
                    .compile(
                        CompileInstructions.builder()
                            .outputDirectory(buildState.compilerOutputDirectory())
                            .classpath(
                                ImmutableList.<File>builder()
                                    .add(buildOutput.deployJar())
                                    .addAll(changedSourceInfo.annotationProcessorClasspath)
                                    .build())
                            .filesToCompile(changedSourceInfo.pathsToCompile)
                            .annotationProcessorClassNames(
                                changedSourceInfo.annotationProcessorClassNames)
                            .outputWriter(writer)
                            .build(),
                        loggingData);
              } else {
                writer.write("No modified files to compile.\n");
              }
              return buildOutput;
            });
  }

  private ChangedSourceInfo getPathsToCompile(
      Label label,
      Map<Label, FastBuildBlazeData> blazeData,
      Set<File> modifiedSinceBuild,
      Writer writer,
      Map<String, String> loggingData)
      throws IOException {

    Stopwatch timer = Stopwatch.createStarted();

    BlazeProjectData projectData = projectDataManager.getBlazeProjectData();
    Set<File> sourceFiles = new HashSet<>();
    Set<String> annotationProcessorClassNames = new HashSet<>();
    // Use ImmutableSet.Builder because it will preserve the classpath order.
    ImmutableSet.Builder<File> annotationProcessorsClasspath = ImmutableSet.builder();
    Set<Label> seenTargets = new HashSet<>();
    recursivelyAddModifiedJavaSources(
        projectData.artifactLocationDecoder,
        blazeData,
        label,
        seenTargets,
        sourceFiles,
        annotationProcessorClassNames,
        annotationProcessorsClasspath,
        modifiedSinceBuild);

    writer.write("Calculated compilation paths in " + timer + "\n");
    loggingData.put(
        "calculate_changed_sources_time_ms", Long.toString(timer.elapsed(TimeUnit.MILLISECONDS)));

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
      Set<File> modifiedSinceBuild) {
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
                    modifiedSinceBuild));
  }

  private static class BlazeConsoleWriter extends Writer {

    final BlazeConsoleService console;

    BlazeConsoleWriter(BlazeConsoleService console) {
      this.console = console;
      console.clear();
    }

    @Override
    public void write(char[] cbuf, int off, int len) {
      console.print(new String(cbuf, off, len), ConsoleViewContentType.NORMAL_OUTPUT);
    }

    @Override
    public void flush() {}

    @Override
    public void close() {}
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
