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
package com.google.idea.blaze.base.sync.aspects;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo;
import com.google.idea.blaze.base.async.FutureUtil;
import com.google.idea.blaze.base.async.executor.BlazeExecutor;
import com.google.idea.blaze.base.async.process.ExternalTask;
import com.google.idea.blaze.base.async.process.LineProcessingOutputStream;
import com.google.idea.blaze.base.bazel.BuildSystemProvider;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.command.BlazeInvocationContext;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper.GetArtifactsException;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelperProvider;
import com.google.idea.blaze.base.command.info.BlazeConfigurationHandler;
import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.google.idea.blaze.base.console.BlazeConsoleLineProcessorProvider;
import com.google.idea.blaze.base.filecache.FileDiffer;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.lang.AdditionalLanguagesHelper;
import com.google.idea.blaze.base.model.BlazeVersionData;
import com.google.idea.blaze.base.model.SyncState;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.prefetch.PrefetchFileSource;
import com.google.idea.blaze.base.prefetch.PrefetchService;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.Result;
import com.google.idea.blaze.base.scope.Scope;
import com.google.idea.blaze.base.scope.ScopedFunction;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.scope.output.PerformanceWarning;
import com.google.idea.blaze.base.scope.output.PrintOutput;
import com.google.idea.blaze.base.scope.scopes.TimingScope;
import com.google.idea.blaze.base.scope.scopes.TimingScope.EventType;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.aspects.BuildResult.Status;
import com.google.idea.blaze.base.sync.aspects.strategy.AspectStrategy;
import com.google.idea.blaze.base.sync.aspects.strategy.AspectStrategy.OutputGroup;
import com.google.idea.blaze.base.sync.projectview.ImportRoots;
import com.google.idea.blaze.base.sync.projectview.LanguageSupport;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.base.sync.sharding.ShardedTargetList;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.pom.NavigatableAdapter;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.Nullable;

/** Implementation of BlazeIdeInterface based on aspects. */
public class BlazeIdeInterfaceAspectsImpl implements BlazeIdeInterface {

  private static final Logger logger = Logger.getInstance(BlazeIdeInterfaceAspectsImpl.class);

  @Override
  public IdeResult updateTargetMap(
      Project project,
      BlazeContext context,
      WorkspaceRoot workspaceRoot,
      ProjectViewSet projectViewSet,
      BlazeInfo blazeInfo,
      BlazeVersionData blazeVersionData,
      BlazeConfigurationHandler configHandler,
      ShardedTargetList shardedTargets,
      WorkspaceLanguageSettings workspaceLanguageSettings,
      ArtifactLocationDecoder artifactLocationDecoder,
      SyncState.Builder syncStateBuilder,
      @Nullable SyncState previousSyncState,
      boolean mergeWithOldState,
      @Nullable TargetMap oldTargetMap) {
    BlazeIdeInterfaceState prevState =
        previousSyncState != null ? previousSyncState.get(BlazeIdeInterfaceState.class) : null;

    // If the language filter has changed, redo everything from scratch
    if (prevState != null
        && !prevState.workspaceLanguageSettings.equals(workspaceLanguageSettings)) {
      prevState = null;
      oldTargetMap = null;
    }

    // If the aspect strategy has changed, redo everything from scratch
    AspectStrategy aspectStrategy = AspectStrategy.getInstance(blazeVersionData.buildSystem());
    if (prevState != null
        && !Objects.equals(prevState.aspectStrategyName, aspectStrategy.getName())) {
      prevState = null;
      oldTargetMap = null;
    }

    IdeInfoResult ideInfoResult =
        getIdeInfo(
            project,
            context,
            workspaceRoot,
            projectViewSet,
            blazeInfo,
            workspaceLanguageSettings.getActiveLanguages(),
            shardedTargets,
            aspectStrategy);
    context.output(PrintOutput.log("ide-info result: " + ideInfoResult.buildResult.status));
    if (ideInfoResult.buildResult.status == BuildResult.Status.FATAL_ERROR) {
      return new IdeResult(oldTargetMap, ideInfoResult.buildResult);
    }
    // If there was a partial error, make a best-effort attempt to sync. Retain
    // any old state that we have in an attempt not to lose too much code.
    if (ideInfoResult.buildResult.status == BuildResult.Status.BUILD_ERROR) {
      mergeWithOldState = true;
    }

    Collection<File> fileList = ideInfoResult.files;
    List<File> updatedFiles = Lists.newArrayList();
    List<File> removedFiles = Lists.newArrayList();
    ImmutableMap<File, Long> fileState;
    try {
      fileState =
          FileDiffer.updateFiles(
              prevState != null ? prevState.fileState : null, fileList, updatedFiles, removedFiles);
    } catch (InterruptedException e) {
      throw new ProcessCanceledException(e);
    } catch (ExecutionException e) {
      IssueOutput.error("Failed to diff aspect output files: " + e).submit(context);
      return new IdeResult(oldTargetMap, BuildResult.FATAL_ERROR);
    }

    // if we're merging with the old state, no files are removed
    int targetCount = fileList.size() + (mergeWithOldState ? removedFiles.size() : 0);
    int removedCount = mergeWithOldState ? 0 : removedFiles.size();

    context.output(
        PrintOutput.log(
            String.format(
                "Total rules: %d, new/changed: %d, removed: %d",
                targetCount, updatedFiles.size(), removedCount)));

    ListenableFuture<?> prefetchFuture =
        PrefetchService.getInstance().prefetchFiles(updatedFiles, true, false);
    if (!FutureUtil.waitForFuture(context, prefetchFuture)
        .timed("FetchAspectOutput", EventType.Prefetching)
        .withProgressMessage("Reading IDE info result...")
        .run()
        .success()) {
      return new IdeResult(oldTargetMap, BuildResult.FATAL_ERROR);
    }

    ImportRoots importRoots =
        ImportRoots.builder(workspaceRoot, Blaze.getBuildSystem(project))
            .add(projectViewSet)
            .build();

    Ref<TargetMap> targetMapReference = Ref.create(oldTargetMap);
    BlazeIdeInterfaceState state =
        updateState(
            project,
            context,
            prevState,
            fileState,
            configHandler,
            workspaceLanguageSettings,
            importRoots,
            aspectStrategy,
            updatedFiles,
            removedFiles,
            mergeWithOldState,
            targetMapReference);
    if (state == null) {
      return new IdeResult(oldTargetMap, BuildResult.FATAL_ERROR);
    }
    syncStateBuilder.put(state);
    return new IdeResult(targetMapReference.get(), ideInfoResult.buildResult);
  }

  private static class IdeInfoResult {
    final Collection<File> files;
    final BuildResult buildResult;

    IdeInfoResult(Collection<File> files, BuildResult buildResult) {
      this.files = files;
      this.buildResult = buildResult;
    }
  }

  private static IdeInfoResult getIdeInfo(
      Project project,
      BlazeContext context,
      WorkspaceRoot workspaceRoot,
      ProjectViewSet projectViewSet,
      BlazeInfo blazeInfo,
      ImmutableSet<LanguageClass> activeLanguages,
      ShardedTargetList shardedTargets,
      AspectStrategy aspectStrategy) {

    Set<File> ideInfoFiles = new LinkedHashSet<>();
    Function<Integer, String> progressMessage =
        count ->
            String.format(
                "Building IDE info files for shard %s of %s...",
                count, shardedTargets.shardedTargets.size());
    Function<List<TargetExpression>, BuildResult> invocation =
        targets -> {
          IdeInfoResult result =
              getIdeInfoForTargets(
                  project,
                  context,
                  workspaceRoot,
                  projectViewSet,
                  blazeInfo,
                  activeLanguages,
                  targets,
                  aspectStrategy);
          ideInfoFiles.addAll(result.files);
          return result.buildResult;
        };
    BuildResult result =
        shardedTargets.runShardedCommand(project, context, progressMessage, invocation);
    return new IdeInfoResult(ideInfoFiles, result);
  }

  /** Runs blaze build with the aspect's ide-info output group for a given set of targets */
  private static IdeInfoResult getIdeInfoForTargets(
      Project project,
      BlazeContext context,
      WorkspaceRoot workspaceRoot,
      ProjectViewSet projectViewSet,
      BlazeInfo blazeInfo,
      ImmutableSet<LanguageClass> activeLanguages,
      List<TargetExpression> targets,
      AspectStrategy aspectStrategy) {
    try (BuildResultHelper buildResultHelper =
        BuildResultHelperProvider.forFilesForSync(
            project, blazeInfo, aspectStrategy.getAspectOutputFilePredicate())) {

      BlazeCommand.Builder builder =
          BlazeCommand.builder(getBinaryPath(project), BlazeCommandName.BUILD)
              .addTargets(targets)
              .addBlazeFlags(BlazeFlags.KEEP_GOING)
              .addBlazeFlags(buildResultHelper.getBuildFlags())
              .addBlazeFlags(
                  BlazeFlags.blazeFlags(
                      project,
                      projectViewSet,
                      BlazeCommandName.BUILD,
                      BlazeInvocationContext.SYNC_CONTEXT));

      aspectStrategy.addAspectAndOutputGroups(builder, OutputGroup.INFO, activeLanguages);

      int retVal =
          ExternalTask.builder(workspaceRoot)
              .addBlazeCommand(builder.build())
              .context(context)
              .stderr(
                  LineProcessingOutputStream.of(
                      BlazeConsoleLineProcessorProvider.getAllStderrLineProcessors(context)))
              .build()
              .run(new TimingScope("ExecuteBlazeCommand", EventType.BlazeInvocation));

      BuildResult buildResult = BuildResult.fromExitCode(retVal);
      if (buildResult.status == Status.FATAL_ERROR) {
        return new IdeInfoResult(ImmutableList.of(), buildResult);
      }
      return Scope.push(
          context,
          childContext -> {
            try {
              childContext.push(new TimingScope("IdeInfoBuildArtifacts", EventType.Other));
              return new IdeInfoResult(buildResultHelper.getBuildArtifacts(), buildResult);
            } catch (GetArtifactsException e) {
              IssueOutput.error("Failed to get ide-info files: " + e.getMessage()).submit(context);
              return new IdeInfoResult(ImmutableList.of(), buildResult);
            }
          });
    }
  }

  private static class TargetFilePair {
    private final File file;
    private final TargetIdeInfo target;

    TargetFilePair(File file, TargetIdeInfo target) {
      this.file = file;
      this.target = target;
    }
  }

  @Nullable
  static BlazeIdeInterfaceState updateState(
      Project project,
      BlazeContext parentContext,
      @Nullable BlazeIdeInterfaceState prevState,
      ImmutableMap<File, Long> fileState,
      BlazeConfigurationHandler configHandler,
      WorkspaceLanguageSettings workspaceLanguageSettings,
      ImportRoots importRoots,
      AspectStrategy aspectStrategy,
      List<File> newFiles,
      List<File> removedFiles,
      boolean mergeWithOldState,
      Ref<TargetMap> targetMapReference) {
    Result<BlazeIdeInterfaceState> result =
        Scope.push(
            parentContext,
            (ScopedFunction<Result<BlazeIdeInterfaceState>>)
                context -> {
                  context.push(new TimingScope("UpdateTargetMap", EventType.Other));

                  // If we're not removing we have to merge the old state
                  // into the new one or we'll miss file removes next time
                  ImmutableMap<File, Long> nextFileState = fileState;
                  if (mergeWithOldState && prevState != null) {
                    ImmutableMap.Builder<File, Long> fileStateBuilder =
                        ImmutableMap.<File, Long>builder().putAll(fileState);
                    for (Map.Entry<File, Long> entry : prevState.fileState.entrySet()) {
                      if (!fileState.containsKey(entry.getKey())) {
                        fileStateBuilder.put(entry);
                      }
                    }
                    nextFileState = fileStateBuilder.build();
                  }

                  BlazeIdeInterfaceState.Builder state = BlazeIdeInterfaceState.builder();
                  state.fileState = nextFileState;
                  state.workspaceLanguageSettings = workspaceLanguageSettings;
                  state.aspectStrategyName = aspectStrategy.getName();

                  Map<TargetKey, TargetIdeInfo> targetMap = Maps.newHashMap();
                  if (prevState != null && !targetMapReference.isNull()) {
                    targetMap.putAll(targetMapReference.get().map());
                    state.fileToTargetMapKey.putAll(prevState.fileToTargetMapKey);
                  }

                  // Update removed unless we're merging with the old state
                  if (!mergeWithOldState) {
                    for (File removedFile : removedFiles) {
                      TargetKey key = state.fileToTargetMapKey.remove(removedFile);
                      if (key != null) {
                        targetMap.remove(key);
                      }
                    }
                  }

                  AtomicLong totalSizeLoaded = new AtomicLong(0);
                  Set<LanguageClass> ignoredLanguages = Sets.newConcurrentHashSet();

                  ListeningExecutorService executor = BlazeExecutor.getInstance().getExecutor();

                  // Read protos from any new files
                  List<ListenableFuture<TargetFilePair>> futures = Lists.newArrayList();
                  for (File file : newFiles) {
                    futures.add(
                        executor.submit(
                            () -> {
                              totalSizeLoaded.addAndGet(file.length());
                              IntellijIdeInfo.TargetIdeInfo message =
                                  aspectStrategy.readAspectFile(file);
                              TargetIdeInfo target =
                                  protoToTarget(
                                      workspaceLanguageSettings,
                                      importRoots,
                                      message,
                                      ignoredLanguages);
                              return new TargetFilePair(file, target);
                            }));
                  }

                  Set<TargetKey> newTargets = new HashSet<>();
                  Set<String> configurations = new LinkedHashSet<>();
                  configurations.add(configHandler.defaultConfigurationPathComponent);

                  // Update state with result from proto files
                  int duplicateTargetLabels = 0;
                  try {
                    for (TargetFilePair targetFilePair : Futures.allAsList(futures).get()) {
                      if (targetFilePair.target != null) {
                        File file = targetFilePair.file;
                        String config = configHandler.getConfigurationPathComponent(file);
                        configurations.add(config);
                        TargetKey key = targetFilePair.target.getKey();
                        if (targetMap.putIfAbsent(key, targetFilePair.target) == null) {
                          state.fileToTargetMapKey.forcePut(file, key);
                        } else {
                          if (!newTargets.add(key)) {
                            duplicateTargetLabels++;
                          }
                          // prioritize the default configuration over build order
                          if (Objects.equals(
                              config, configHandler.defaultConfigurationPathComponent)) {
                            targetMap.put(key, targetFilePair.target);
                            state.fileToTargetMapKey.forcePut(file, key);
                          }
                        }
                      }
                    }
                  } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return Result.error(null);
                  } catch (ExecutionException e) {
                    return Result.error(e);
                  }

                  context.output(
                      PrintOutput.log(
                          String.format(
                              "Loaded %d aspect files, total size %dkB",
                              newFiles.size(), totalSizeLoaded.get() / 1024)));
                  if (duplicateTargetLabels > 0) {
                    context.output(
                        new PerformanceWarning(
                            String.format(
                                "There were %d duplicate rules, built with the following "
                                    + "configurations: %s.\nYour IDE sync is slowed down by ~%d%%.",
                                duplicateTargetLabels,
                                configurations,
                                (100 * duplicateTargetLabels / targetMap.size()))));
                  }

                  ignoredLanguages.retainAll(
                      LanguageSupport.availableAdditionalLanguages(
                          workspaceLanguageSettings.getWorkspaceType()));
                  warnIgnoredLanguages(project, context, ignoredLanguages);

                  targetMapReference.set(new TargetMap(ImmutableMap.copyOf(targetMap)));
                  return Result.of(state.build());
                });

    if (result.error != null) {
      logger.error(result.error);
      return null;
    }
    return result.result;
  }

  @Nullable
  private static TargetIdeInfo protoToTarget(
      WorkspaceLanguageSettings languageSettings,
      ImportRoots importRoots,
      IntellijIdeInfo.TargetIdeInfo message,
      Set<LanguageClass> ignoredLanguages) {
    Kind kind = Kind.fromProto(message);
    if (kind == null) {
      return null;
    }
    if (languageSettings.isLanguageActive(kind.getLanguageClass())) {
      return TargetIdeInfo.fromProto(message);
    }
    TargetKey key = message.hasKey() ? TargetKey.fromProto(message.getKey()) : null;
    if (key != null && importRoots.importAsSource(key.getLabel())) {
      ignoredLanguages.add(kind.getLanguageClass());
    }
    return null;
  }

  private static void warnIgnoredLanguages(
      Project project, BlazeContext context, Set<LanguageClass> ignoredLangs) {
    if (ignoredLangs.isEmpty()) {
      return;
    }
    List<LanguageClass> sorted = new ArrayList<>(ignoredLangs);
    sorted.sort(Ordering.usingToString());

    String msg =
        "Some project targets were ignored because the corresponding language support "
            + "isn't enabled. Click here to enable support for: "
            + Joiner.on(", ").join(sorted);
    IssueOutput.warn(msg)
        .navigatable(
            new NavigatableAdapter() {
              @Override
              public void navigate(boolean requestFocus) {
                AdditionalLanguagesHelper.enableLanguageSupport(project, sorted);
              }
            })
        .submit(context);
  }

  @Override
  public BuildResult resolveIdeArtifacts(
      Project project,
      BlazeContext context,
      WorkspaceRoot workspaceRoot,
      ProjectViewSet projectViewSet,
      BlazeInfo blazeInfo,
      BlazeVersionData blazeVersionData,
      WorkspaceLanguageSettings workspaceLanguageSettings,
      ShardedTargetList shardedTargets) {
    Function<Integer, String> progressMessage =
        count ->
            String.format(
                "Building IDE resolve files for shard %s of %s...",
                count, shardedTargets.shardedTargets.size());
    Function<List<TargetExpression>, BuildResult> invocation =
        targets ->
            doResolveIdeArtifacts(
                project,
                context,
                workspaceRoot,
                projectViewSet,
                blazeInfo,
                blazeVersionData,
                workspaceLanguageSettings,
                targets);
    return shardedTargets.runShardedCommand(project, context, progressMessage, invocation);
  }

  @Override
  public BuildResult compileIdeArtifacts(
      Project project,
      BlazeContext context,
      WorkspaceRoot workspaceRoot,
      ProjectViewSet projectViewSet,
      BlazeVersionData blazeVersionData,
      WorkspaceLanguageSettings workspaceLanguageSettings,
      ShardedTargetList shardedTargets) {
    Function<Integer, String> progressMessage =
        count ->
            String.format(
                "Building IDE resolve files for shard %s of %s...",
                count, shardedTargets.shardedTargets.size());
    Function<List<TargetExpression>, BuildResult> invocation =
        targets ->
            doCompileIdeArtifacts(
                project,
                context,
                workspaceRoot,
                projectViewSet,
                blazeVersionData,
                workspaceLanguageSettings,
                targets);
    return shardedTargets.runShardedCommand(project, context, progressMessage, invocation);
  }

  /**
   * Blaze build invocation requesting the 'intellij-resolve' aspect output group.
   *
   * <p>Prefetches the output artifacts built by this invocation.
   */
  private static BuildResult doResolveIdeArtifacts(
      Project project,
      BlazeContext context,
      WorkspaceRoot workspaceRoot,
      ProjectViewSet projectViewSet,
      BlazeInfo blazeInfo,
      BlazeVersionData blazeVersionData,
      WorkspaceLanguageSettings workspaceLanguageSettings,
      List<TargetExpression> targets) {
    try (BuildResultHelper buildResultHelper =
        BuildResultHelperProvider.forFilesForSync(project, blazeInfo, getGenfilePrefetchFilter())) {

      BlazeCommand.Builder blazeCommandBuilder =
          BlazeCommand.builder(getBinaryPath(project), BlazeCommandName.BUILD)
              .addTargets(targets)
              .addBlazeFlags(BlazeFlags.KEEP_GOING)
              .addBlazeFlags(buildResultHelper.getBuildFlags())
              .addBlazeFlags(
                  BlazeFlags.blazeFlags(
                      project,
                      projectViewSet,
                      BlazeCommandName.BUILD,
                      BlazeInvocationContext.SYNC_CONTEXT));

      // Request the 'intellij-resolve' aspect output group.
      AspectStrategy.getInstance(blazeVersionData.buildSystem())
          .addAspectAndOutputGroups(
              blazeCommandBuilder,
              OutputGroup.RESOLVE,
              workspaceLanguageSettings.getActiveLanguages());

      // Run the blaze build command, parsing any output artifacts produced.
      int retVal =
          ExternalTask.builder(workspaceRoot)
              .addBlazeCommand(blazeCommandBuilder.build())
              .context(context)
              .stderr(
                  LineProcessingOutputStream.of(
                      BlazeConsoleLineProcessorProvider.getAllStderrLineProcessors(context)))
              .build()
              .run(new TimingScope("ExecuteBlazeCommand", EventType.BlazeInvocation));

      BuildResult result = BuildResult.fromExitCode(retVal);
      if (result.status != BuildResult.Status.FATAL_ERROR) {
        Scope.push(
            context,
            childContext -> {
              childContext.push(new TimingScope("GenfilesPrefetchBuildArtifacts", EventType.Other));
              try {
                prefetchGenfiles(context, buildResultHelper.getBuildArtifacts());
              } catch (GetArtifactsException e) {
                IssueOutput.warn("Failed to get genfiles to prefetch: " + e.getMessage())
                    .submit(context);
              }
            });
      }
      return result;
    }
  }

  /** Blaze build invocation requesting the 'intellij-compile' aspect output group. */
  private static BuildResult doCompileIdeArtifacts(
      Project project,
      BlazeContext context,
      WorkspaceRoot workspaceRoot,
      ProjectViewSet projectViewSet,
      BlazeVersionData blazeVersionData,
      WorkspaceLanguageSettings workspaceLanguageSettings,
      List<TargetExpression> targets) {
    BlazeCommand.Builder blazeCommandBuilder =
        BlazeCommand.builder(getBinaryPath(project), BlazeCommandName.BUILD)
            .addTargets(targets)
            .addBlazeFlags()
            .addBlazeFlags(BlazeFlags.KEEP_GOING)
            .addBlazeFlags(
                BlazeFlags.blazeFlags(
                    project,
                    projectViewSet,
                    BlazeCommandName.BUILD,
                    BlazeInvocationContext.SYNC_CONTEXT));

    AspectStrategy.getInstance(blazeVersionData.buildSystem())
        .addAspectAndOutputGroups(
            blazeCommandBuilder,
            OutputGroup.COMPILE,
            workspaceLanguageSettings.getActiveLanguages());

    // Run the blaze build command.
    int retVal =
        ExternalTask.builder(workspaceRoot)
            .addBlazeCommand(blazeCommandBuilder.build())
            .context(context)
            .stderr(
                LineProcessingOutputStream.of(
                    BlazeConsoleLineProcessorProvider.getAllStderrLineProcessors(context)))
            .build()
            .run();

    return BuildResult.fromExitCode(retVal);
  }

  /** A filename filter for blaze output artifacts to prefetch. */
  private static Predicate<String> getGenfilePrefetchFilter() {
    ImmutableSet<String> extensions = PrefetchFileSource.getAllPrefetchFileExtensions();
    return fileName -> extensions.contains(FileUtil.getExtension(fileName));
  }

  /** Prefetch a list of blaze output artifacts, blocking until complete. */
  private static void prefetchGenfiles(BlazeContext context, ImmutableList<File> artifacts) {
    ListenableFuture<?> prefetchFuture =
        PrefetchService.getInstance().prefetchFiles(artifacts, false, false);
    FutureUtil.waitForFuture(context, prefetchFuture)
        .timed("PrefetchGenfiles", EventType.Prefetching)
        .withProgressMessage("Prefetching genfiles...")
        .run();
  }

  private static String getBinaryPath(Project project) {
    BuildSystemProvider buildSystemProvider = Blaze.getBuildSystemProvider(project);
    return buildSystemProvider.getSyncBinaryPath(project);
  }
}
