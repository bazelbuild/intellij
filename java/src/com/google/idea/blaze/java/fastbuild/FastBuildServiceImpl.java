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
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.async.executor.ProgressiveTaskWithProgressIndicator;
import com.google.idea.blaze.base.async.process.ExternalTask;
import com.google.idea.blaze.base.async.process.LineProcessingOutputStream;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.command.BlazeInvocationContext;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper.GetArtifactsException;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelperProvider;
import com.google.idea.blaze.base.console.BlazeConsoleLineProcessorProvider;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.run.BlazeCommandRunConfigurationType;
import com.google.idea.blaze.base.run.ExecutorType;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.ScopedTask;
import com.google.idea.blaze.base.scope.output.StatusOutput;
import com.google.idea.blaze.base.settings.BuildSystem;
import com.google.idea.blaze.base.sync.aspects.BuildResult;
import com.google.idea.blaze.base.sync.aspects.BuildResult.Status;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.java.AndroidBlazeRules;
import com.google.idea.blaze.java.JavaBlazeRules;
import com.google.idea.blaze.java.fastbuild.FastBuildChangedFilesService.ChangedSources;
import com.google.idea.blaze.java.fastbuild.FastBuildLogDataScope.FastBuildLogOutput;
import com.google.idea.blaze.java.fastbuild.FastBuildState.BuildOutput;
import com.google.idea.common.concurrency.ConcurrencyUtil;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.InvokeAfterUpdateMode;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Stream;
import javax.annotation.Nullable;

final class FastBuildServiceImpl implements FastBuildService, ProjectComponent {

  private static final BoolExperiment useVfsListenerFiles =
      new BoolExperiment("fast.build.vfs.listener", true);

  private static final ImmutableSetMultimap<BuildSystem, Kind> SUPPORTED_KINDS =
      ImmutableSetMultimap.<BuildSystem, Kind>builder()
          .putAll(BuildSystem.Bazel, JavaBlazeRules.RuleTypes.JAVA_TEST.getKind())
          .putAll(
              BuildSystem.Blaze,
              AndroidBlazeRules.RuleTypes.ANDROID_ROBOLECTRIC_TEST.getKind(),
              AndroidBlazeRules.RuleTypes.ANDROID_LOCAL_TEST.getKind(),
              JavaBlazeRules.RuleTypes.JAVA_TEST.getKind())
          .build();

  private final Project project;
  private final ProjectViewManager projectViewManager;
  private final BlazeProjectDataManager projectDataManager;
  private final ChangeListManager changeListManager;
  private final FastBuildIncrementalCompiler incrementalCompiler;
  private final FastBuildChangedFilesService changedFilesManager;
  private final Thread shutdownHook;

  private final ConcurrentHashMap<Label, FastBuildState> builds;

  FastBuildServiceImpl(
      Project project,
      ProjectViewManager projectViewManager,
      BlazeProjectDataManager projectDataManager,
      ChangeListManager changeListManager,
      FastBuildIncrementalCompiler incrementalCompiler,
      FastBuildChangedFilesService changedFilesManager) {
    this.project = project;
    this.projectViewManager = projectViewManager;
    this.projectDataManager = projectDataManager;
    this.changeListManager = changeListManager;
    this.incrementalCompiler = incrementalCompiler;
    this.changedFilesManager = changedFilesManager;
    this.builds = new ConcurrentHashMap<>();
    this.shutdownHook = new Thread(this::resetBuilds);
  }

  @Override
  public boolean supportsFastBuilds(BuildSystem buildSystem, Kind kind) {
    return SUPPORTED_KINDS.get(buildSystem).contains(kind);
  }

  @Override
  public void resetBuild(Label label) {
    FastBuildState build = builds.remove(label);
    if (build != null) {
      FileUtil.delete(build.compilerOutputDirectory());
    }
  }

  @Override
  public Future<FastBuildInfo> createBuild(
      BlazeContext context, Label label, String blazeBinaryPath, List<String> blazeFlags)
      throws FastBuildException {

    try {
      FastBuildParameters buildParameters = generateBuildParameters(blazeBinaryPath, blazeFlags);
      FastBuildState buildState =
          builds.compute(
              label,
              (unused, buildInfo) -> updateBuild(context, label, buildParameters, buildInfo));
      return Futures.transform(
          buildState.newBuildOutput(),
          buildOutput ->
              FastBuildInfo.create(
                  label,
                  buildOutput.deployJar(),
                  ImmutableList.of(buildState.compilerOutputDirectory(), buildOutput.deployJar()),
                  buildOutput.blazeData()),
          directExecutor());
    } catch (FastBuildTunnelException e) {
      throw e.asFastBuildException();
    }
  }

  private FastBuildParameters generateBuildParameters(
      String blazeBinaryPath, List<String> userBlazeFlags) {

    ProjectViewSet projectViewSet = projectViewManager.getProjectViewSet();
    List<String> projectBlazeFlags =
        BlazeFlags.blazeFlags(
            project,
            projectViewSet,
            BlazeCommandName.BUILD,
            BlazeInvocationContext.runConfigContext(
                ExecutorType.FAST_BUILD_RUN, BlazeCommandRunConfigurationType.getInstance(), true));
    return FastBuildParameters.builder()
        .setBlazeBinary(blazeBinaryPath)
        // TODO(b/64714884): reenable this once one version enforcement is turned on for java_tests
        // Right now there's a discrepancy because enforcement is disabled for java_test rules, but
        // turns back on if you build a java_test_deploy.jar (as we do). So force it off for the
        // deploy jar too.
        .addBlazeFlags(ImmutableList.of("--experimental_one_version_enforcement=off"))
        .addBlazeFlags(projectBlazeFlags)
        .addBlazeFlags(userBlazeFlags)
        .build();
  }

  private FastBuildState updateBuild(
      BlazeContext context,
      Label label,
      FastBuildParameters buildParameters,
      @Nullable FastBuildState existingBuildState) {

    context.output(FastBuildLogOutput.keyValue("label", label.toString()));

    if (existingBuildState != null && !existingBuildState.newBuildOutput().isDone()) {
      // Don't start a new build if an existing one is still running.
      context.output(FastBuildLogOutput.keyValue("reused_existing_build_future", "true"));
      return existingBuildState;
    }

    BuildOutput completedBuildOutput = getCompletedBuild(existingBuildState);

    boolean useVfsListener = useVfsListenerFiles.getValue();

    Set<File> vcsModifiedFiles = ImmutableSet.of();
    if (!useVfsListener) {
      Stopwatch timer = Stopwatch.createStarted();
      vcsModifiedFiles = getVcsModifiedFiles();
      context.output(FastBuildLogOutput.milliseconds("retrieve_modified_files_time_ms", timer));
    }

    boolean tooManyVfsModifiedFiles = false;
    Set<File> vfsModifiedFiles = null;
    if (completedBuildOutput != null && useVfsListener) {
      ChangedSources changedSources = changedFilesManager.getAndResetChangedSources(label);
      vfsModifiedFiles = changedSources.changedSources();
      if (changedSources.needsFullCompile()) {
        tooManyVfsModifiedFiles = true;
      }
    }

    if (completedBuildOutput == null || tooManyVfsModifiedFiles) {
      File compileDirectory = getCompilerOutputDirectory(existingBuildState);
      ListenableFuture<BuildOutput> newBuildOutput =
          buildDeployJar(context, label, buildParameters);
      changedFilesManager.newBuild(label, newBuildOutput);
      return FastBuildState.create(
          newBuildOutput, compileDirectory, buildParameters, vcsModifiedFiles);
    } else {
      existingBuildState =
          existingBuildState
              .withAdditionalModifiedFiles(vcsModifiedFiles)
              .withCompletedBuildOutput(completedBuildOutput);
      return performIncrementalCompilation(context, label, existingBuildState, vfsModifiedFiles);
    }
  }

  private static File getCompilerOutputDirectory(@Nullable FastBuildState buildState) {
    if (buildState == null || !buildState.compilerOutputDirectory().exists()) {
      return createCompilerOutputDirectory();
    }
    return buildState.compilerOutputDirectory();
  }

  private static File createCompilerOutputDirectory() {
    try {
      return Files.createTempDirectory("ide-fastbuild-").toFile();
    } catch (IOException e) {
      throw new FastBuildTunnelException(e);
    }
  }

  private Set<File> getVcsModifiedFiles() {
    Set<File> modifiedPaths = new HashSet<>();
    changeListManager.invokeAfterUpdate(
        () -> addAllModifiedPaths(modifiedPaths),
        InvokeAfterUpdateMode.SYNCHRONOUS_CANCELLABLE,
        "Retrieving list of modified files",
        ModalityState.NON_MODAL);
    return modifiedPaths;
  }

  private void addAllModifiedPaths(Set<File> modifiedPaths) {
    changeListManager.getAllChanges().stream()
        .flatMap(change -> Stream.of(change.getBeforeRevision(), change.getAfterRevision()))
        .filter(Objects::nonNull)
        .map(ContentRevision::getFile)
        .filter(filePath -> !filePath.isNonLocal() && !filePath.isDirectory())
        .forEach(filePath -> modifiedPaths.add(filePath.getIOFile()));
  }

  @Nullable
  private static BuildOutput getCompletedBuild(@Nullable FastBuildState buildState) {
    if (buildState == null || !buildState.compilerOutputDirectory().exists()) {
      return null;
    }

    BuildOutput buildOutput;
    try {
      buildOutput = buildState.newBuildOutput().get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError("InterruptedException caught when calling get() on a done future");
    } catch (CancellationException | ExecutionException e) {
      // Whoever initially launched this build will log the exception, so we don't need to worry
      // about it.
      buildOutput = buildState.completedBuildOutput().orElse(null);
    }

    return buildOutput != null && buildOutput.deployJar().exists() ? buildOutput : null;
  }

  private ListenableFuture<FastBuildState.BuildOutput> buildDeployJar(
      BlazeContext context, Label label, FastBuildParameters buildParameters) {

    Label deployJarLabel = createDeployJarLabel(label);
    WorkspaceRoot workspaceRoot = WorkspaceRoot.fromProject(project);

    FastBuildAspectStrategy aspectStrategy =
        FastBuildAspectStrategy.getInstance(
            projectDataManager.getBlazeProjectData().getBlazeVersionData().buildSystem());
    @SuppressWarnings("MustBeClosedChecker") // close buildResultHelper manually via a listener
    BuildResultHelper buildResultHelper =
        BuildResultHelperProvider.forFiles(
            project,
            file ->
                file.endsWith(deployJarLabel.targetName().toString())
                    || aspectStrategy.getAspectOutputFilePredicate().test(file));

    Stopwatch timer = Stopwatch.createUnstarted();

    ListenableFuture<BuildResult> buildResultFuture =
        ProgressiveTaskWithProgressIndicator.builder(project, "Building deploy jar for fast builds")
            .submitTaskWithResult(
                new ScopedTask<BuildResult>(context) {
                  @Override
                  protected BuildResult execute(BlazeContext context) {
                    context.output(
                        new StatusOutput(
                            "Building base deploy jar for fast builds: "
                                + deployJarLabel.targetName()));

                    BlazeCommand.Builder command =
                        BlazeCommand.builder(buildParameters.blazeBinary(), BlazeCommandName.BUILD)
                            .addTargets(label)
                            .addTargets(deployJarLabel)
                            .addBlazeFlags(buildParameters.blazeFlags())
                            .addBlazeFlags(buildResultHelper.getBuildFlags());

                    aspectStrategy.addAspectAndOutputGroups(
                        command, /* additionalOutputGroups= */ "default");

                    timer.start();
                    int exitCode =
                        ExternalTask.builder(workspaceRoot)
                            .addBlazeCommand(command.build())
                            .context(context)
                            .stderr(
                                LineProcessingOutputStream.of(
                                    BlazeConsoleLineProcessorProvider.getAllStderrLineProcessors(
                                        context)))
                            .build()
                            .run();
                    return BuildResult.fromExitCode(exitCode);
                  }
                });

    ListenableFuture<BuildOutput> buildOutputFuture =
        Futures.transform(
            buildResultFuture,
            result -> {
              context.output(
                  FastBuildLogOutput.keyValue("deploy_jar_build_result", result.status.toString()));
              context.output(FastBuildLogOutput.milliseconds("deploy_jar_build_time_ms", timer));
              if (result.status != Status.SUCCESS) {
                throw new RuntimeException("Blaze failure building deploy jar");
              }
              try {
                ImmutableList<File> deployJarArtifacts =
                    buildResultHelper.getBuildArtifactsForTarget(deployJarLabel);
                checkState(deployJarArtifacts.size() == 1);
                File deployJar = deployJarArtifacts.get(0);

                ImmutableList<File> ideInfoFiles =
                    buildResultHelper.getArtifactsForOutputGroups(
                        ImmutableSet.of(aspectStrategy.getAspectOutputGroup()));

                ImmutableMap<Label, FastBuildBlazeData> blazeData =
                    ideInfoFiles.stream()
                        .map(aspectStrategy::readFastBuildBlazeData)
                        .collect(toImmutableMap(FastBuildBlazeData::label, i -> i));
                return BuildOutput.create(deployJar, blazeData);
              } catch (GetArtifactsException e) {
                throw new RuntimeException("Blaze failure building deploy jar: " + e.getMessage());
              }
            },
            ConcurrencyUtil.getAppExecutorService());
    buildOutputFuture.addListener(
        buildResultHelper::close, ConcurrencyUtil.getAppExecutorService());
    return buildOutputFuture;
  }

  private Label createDeployJarLabel(Label label) {
    return Label.create(label + "_deploy.jar");
  }

  private FastBuildState performIncrementalCompilation(
      BlazeContext context,
      Label label,
      FastBuildState existingBuildState,
      Set<File> vfsModifiedFiles) {

    ListenableFuture<BuildOutput> compilationResult =
        incrementalCompiler.compile(context, label, existingBuildState, vfsModifiedFiles);
    Futures.addCallback(
        compilationResult,
        new FutureCallback<BuildOutput>() {
          @Override
          public void onSuccess(BuildOutput result) {}

          @Override
          public void onFailure(Throwable t) {
            // We want to unconditionally try to recompile these files, even if they haven't changed
            // again. It's sort of silly, but otherwise you get into this situation:
            // 1. Compile a successful build and run tests.
            // 2. Add a syntax error to a file
            // 3. Recompile; compilation fails.
            // 4. Run fast build again immediately. No files have changed, so compilation is
            //    skipped. The tests will run and pass, despite the syntax error.
            changedFilesManager.addFilesFromFailedCompilation(label, vfsModifiedFiles);
          }
        },
        directExecutor());
    return existingBuildState.withNewBuildOutput(compilationResult);
  }

  private static class FastBuildTunnelException extends RuntimeException {

    FastBuildTunnelException(Throwable cause) {
      super(cause);
    }

    FastBuildException asFastBuildException() {
      Throwable cause = getCause();
      return cause instanceof FastBuildException
          ? (FastBuildException) cause
          : new FastBuildException(cause);
    }
  }

  @Override
  public void projectOpened() {
    Runtime.getRuntime().addShutdownHook(shutdownHook);
  }

  @Override
  public void projectClosed() {
    Runtime.getRuntime().removeShutdownHook(shutdownHook);
    resetBuilds();
  }

  private void resetBuilds() {
    builds.keySet().forEach(this::resetBuild);
  }
}
