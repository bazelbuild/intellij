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
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.console.BlazeConsoleService;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.java.fastbuild.FastBuildCompiler.CompileInstructions;
import com.google.idea.blaze.java.fastbuild.FastBuildState.BuildOutput;
import com.google.idea.common.concurrency.ConcurrencyUtil;
import com.intellij.execution.ui.ConsoleViewContentType;
import java.io.File;
import java.io.Writer;
import java.util.HashSet;
import java.util.Set;

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
  public ListenableFuture<BuildOutput> compile(Label label, FastBuildState buildState) {
    checkState(buildState.completedBuildOutput().isPresent());
    BuildOutput buildOutput = buildState.completedBuildOutput().get();
    checkState(buildOutput.targetMap().contains(TargetKey.forPlainTarget(label)));

    return ConcurrencyUtil.getAppExecutorService()
        .submit(
            () -> {
              BlazeConsoleWriter writer = new BlazeConsoleWriter(blazeConsoleService);

              Stopwatch stopwatch = Stopwatch.createStarted();
              Set<File> pathsToCompile =
                  getPathsToCompile(label, buildOutput.targetMap(), buildState.modifiedFiles());
              writer.write("Calculated compilation paths in " + stopwatch + "\n");
              if (!pathsToCompile.isEmpty()) {
                compilerFactory
                    .getCompilerFor(buildOutput.targetMap().get(TargetKey.forPlainTarget(label)))
                    .compile(
                        CompileInstructions.builder()
                            .outputDirectory(buildState.compilerOutputDirectory())
                            .classpath(ImmutableList.of(buildOutput.deployJar()))
                            .filesToCompile(pathsToCompile)
                            .outputWriter(writer)
                            .build());
                writer.write("Compilation finished in " + stopwatch + "\n");
              } else {
                writer.write("No modified files to compile.\n");
              }
              return buildOutput;
            });
  }

  private Set<File> getPathsToCompile(
      Label label, TargetMap targetMap, Set<File> modifiedSinceBuild) {
    BlazeProjectData projectData = projectDataManager.getBlazeProjectData();
    Set<File> sourceFiles = new HashSet<>();
    Set<TargetKey> seenTargets = new HashSet<>();
    recursivelyAddModifiedJavaSources(
        projectData.artifactLocationDecoder,
        targetMap,
        TargetKey.forPlainTarget(label),
        seenTargets,
        sourceFiles,
        modifiedSinceBuild);
    return sourceFiles;
  }

  private void recursivelyAddModifiedJavaSources(
      ArtifactLocationDecoder artifactLocationDecoder,
      TargetMap targetMap,
      TargetKey targetKey,
      Set<TargetKey> seenTargets,
      Set<File> sourceFiles,
      Set<File> modifiedSinceBuild) {
    if (seenTargets.contains(targetKey)) {
      return;
    }

    seenTargets.add(targetKey);

    TargetIdeInfo targetIdeInfo = targetMap.get(targetKey);
    if (targetIdeInfo == null) {
      return;
    }
    artifactLocationDecoder
        .decodeAll(targetIdeInfo.sources)
        .stream()
        .filter(file -> file.getName().endsWith(".java"))
        .filter(modifiedSinceBuild::contains)
        .forEach(sourceFiles::add);
    targetIdeInfo.dependencies.forEach(
        dep ->
            recursivelyAddModifiedJavaSources(
                artifactLocationDecoder,
                targetMap,
                dep.targetKey,
                seenTargets,
                sourceFiles,
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
}
