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
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
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
import com.google.idea.blaze.base.command.info.BlazeConfigurationHandler;
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
import com.google.idea.blaze.base.settings.BuildSystem;
import com.google.idea.blaze.base.sync.aspects.BuildResult.Status;
import com.google.idea.blaze.base.sync.aspects.strategy.AspectStrategy;
import com.google.idea.blaze.base.sync.aspects.strategy.AspectStrategy.OutputGroup;
import com.google.idea.blaze.base.sync.aspects.strategy.AspectStrategyProvider;
import com.google.idea.blaze.base.sync.projectview.ImportRoots;
import com.google.idea.blaze.base.sync.projectview.LanguageSupport;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.base.sync.sharding.ShardedTargetList;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.pom.NavigatableAdapter;
import java.io.File;
import java.io.Serializable;
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

  static class State implements Serializable {
    private static final long serialVersionUID = 15L;
    TargetMap targetMap;
    ImmutableMap<File, Long> fileState = null;
    BiMap<File, TargetKey> fileToTargetMapKey = HashBiMap.create();
    WorkspaceLanguageSettings workspaceLanguageSettings;
    String aspectStrategyName;
  }

  @Override
  public IdeResult updateTargetMap(
      Project project,
      BlazeContext context,
      WorkspaceRoot workspaceRoot,
      ProjectViewSet projectViewSet,
      BlazeVersionData blazeVersionData,
      BlazeConfigurationHandler configHandler,
      ShardedTargetList shardedTargets,
      WorkspaceLanguageSettings workspaceLanguageSettings,
      ArtifactLocationDecoder artifactLocationDecoder,
      SyncState.Builder syncStateBuilder,
      @Nullable SyncState previousSyncState,
      boolean mergeWithOldState) {
    State prevState = previousSyncState != null ? previousSyncState.get(State.class) : null;

    // If the language filter has changed, redo everything from scratch
    if (prevState != null
        && !prevState.workspaceLanguageSettings.equals(workspaceLanguageSettings)) {
      prevState = null;
    }

    // If the aspect strategy has changed, redo everything from scratch
    final AspectStrategy aspectStrategy =
        AspectStrategyProvider.findAspectStrategy(blazeVersionData);
    if (prevState != null
        && !Objects.equals(prevState.aspectStrategyName, aspectStrategy.getName())) {
      prevState = null;
    }

    IdeInfoResult ideInfoResult =
        getIdeInfo(
            project,
            context,
            workspaceRoot,
            projectViewSet,
            workspaceLanguageSettings.activeLanguages,
            shardedTargets,
            aspectStrategy);
    context.output(PrintOutput.log("ide-info result: " + ideInfoResult.buildResult.status));
    if (ideInfoResult.buildResult.status == BuildResult.Status.FATAL_ERROR) {
      return new IdeResult(
          prevState != null ? prevState.targetMap : null, ideInfoResult.buildResult);
    }
    // If there was a partial error, make a best-effort attempt to sync. Retain
    // any old state that we have in an attempt not to lose too much code.
    if (ideInfoResult.buildResult.status == BuildResult.Status.BUILD_ERROR) {
      mergeWithOldState = true;
    }

    Collection<File> fileList = ideInfoResult.files;
    List<File> updatedFiles = Lists.newArrayList();
    List<File> removedFiles = Lists.newArrayList();
    ImmutableMap<File, Long> fileState =
        FileDiffer.updateFiles(
            prevState != null ? prevState.fileState : null, fileList, updatedFiles, removedFiles);
    if (fileState == null) {
      return new IdeResult(prevState != null ? prevState.targetMap : null, BuildResult.FATAL_ERROR);
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
      return new IdeResult(prevState != null ? prevState.targetMap : null, BuildResult.FATAL_ERROR);
    }

    ImportRoots importRoots =
        ImportRoots.builder(workspaceRoot, Blaze.getBuildSystem(project))
            .add(projectViewSet)
            .build();

    State state =
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
            mergeWithOldState);
    if (state == null) {
      return new IdeResult(prevState != null ? prevState.targetMap : null, BuildResult.FATAL_ERROR);
    }
    syncStateBuilder.put(State.class, state);
    return new IdeResult(state.targetMap, ideInfoResult.buildResult);
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
      BlazeContext parentContext,
      WorkspaceRoot workspaceRoot,
      ProjectViewSet projectViewSet,
      ImmutableSet<LanguageClass> activeLanguages,
      ShardedTargetList shardedTargets,
      AspectStrategy aspectStrategy) {
    return Scope.push(
        parentContext,
        context -> {
          context.push(
              new TimingScope(
                  String.format("Execute%sCommand", Blaze.buildSystemName(project)),
                  EventType.BlazeInvocation));
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
                        activeLanguages,
                        targets,
                        aspectStrategy);
                ideInfoFiles.addAll(result.files);
                return result.buildResult;
              };
          BuildResult result =
              shardedTargets.runShardedCommand(project, context, progressMessage, invocation);
          return new IdeInfoResult(ideInfoFiles, result);
        });
  }

  /** Runs blaze build with the aspect's ide-info output group for a given set of targets */
  private static IdeInfoResult getIdeInfoForTargets(
      Project project,
      BlazeContext context,
      WorkspaceRoot workspaceRoot,
      ProjectViewSet projectViewSet,
      ImmutableSet<LanguageClass> activeLanguages,
      List<TargetExpression> targets,
      AspectStrategy aspectStrategy) {
    try (BuildResultHelper buildResultHelper =
        BuildResultHelper.forFiles(aspectStrategy.getAspectOutputFilePredicate())) {

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
                      BlazeInvocationContext.Sync,
                      null));

      aspectStrategy.addAspectAndOutputGroups(builder, OutputGroup.INFO, activeLanguages);

      int retVal =
          ExternalTask.builder(workspaceRoot)
              .addBlazeCommand(builder.build())
              .context(context)
              .stderr(
                  LineProcessingOutputStream.of(
                      BlazeConsoleLineProcessorProvider.getAllStderrLineProcessors(context)))
              .build()
              .run();

      BuildResult buildResult = BuildResult.fromExitCode(retVal);
      if (buildResult.status == Status.FATAL_ERROR) {
        return new IdeInfoResult(ImmutableList.of(), buildResult);
      }
      return new IdeInfoResult(buildResultHelper.getBuildArtifacts(), buildResult);
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
  static State updateState(
      Project project,
      BlazeContext parentContext,
      @Nullable State prevState,
      ImmutableMap<File, Long> fileState,
      BlazeConfigurationHandler configHandler,
      WorkspaceLanguageSettings workspaceLanguageSettings,
      ImportRoots importRoots,
      AspectStrategy aspectStrategy,
      List<File> newFiles,
      List<File> removedFiles,
      boolean mergeWithOldState) {
    Result<State> result =
        Scope.push(
            parentContext,
            (ScopedFunction<Result<State>>)
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

                  State state = new State();
                  state.fileState = nextFileState;
                  state.workspaceLanguageSettings = workspaceLanguageSettings;
                  state.aspectStrategyName = aspectStrategy.getName();

                  Map<TargetKey, TargetIdeInfo> targetMap = Maps.newHashMap();
                  if (prevState != null) {
                    targetMap.putAll(prevState.targetMap.map());
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
                        TargetKey key = targetFilePair.target.key;
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

                  state.targetMap = new TargetMap(ImmutableMap.copyOf(targetMap));
                  return Result.of(state);
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
    Kind kind = IdeInfoFromProtobuf.getKind(message);
    if (kind == null) {
      return null;
    }
    if (languageSettings.isLanguageActive(kind.languageClass)) {
      return IdeInfoFromProtobuf.makeTargetIdeInfo(message);
    }
    TargetKey key = IdeInfoFromProtobuf.getKey(message);
    if (key != null && importRoots.importAsSource(key.label)) {
      ignoredLanguages.add(kind.languageClass);
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
      BlazeVersionData blazeVersionData,
      WorkspaceLanguageSettings workspaceLanguageSettings,
      ShardedTargetList shardedTargets) {
    return resolveIdeArtifacts(
        project,
        context,
        workspaceRoot,
        projectViewSet,
        blazeVersionData,
        workspaceLanguageSettings,
        shardedTargets,
        false);
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
    boolean ideCompile = hasIdeCompileOutputGroup(blazeVersionData);
    return resolveIdeArtifacts(
        project,
        context,
        workspaceRoot,
        projectViewSet,
        blazeVersionData,
        workspaceLanguageSettings,
        shardedTargets,
        ideCompile);
  }

  private static boolean hasIdeCompileOutputGroup(BlazeVersionData blazeVersionData) {
    return blazeVersionData.buildSystem() == BuildSystem.Blaze
        || blazeVersionData.bazelIsAtLeastVersion(0, 4, 4);
  }

  private static BuildResult resolveIdeArtifacts(
      Project project,
      BlazeContext context,
      WorkspaceRoot workspaceRoot,
      ProjectViewSet projectViewSet,
      BlazeVersionData blazeVersionData,
      WorkspaceLanguageSettings workspaceLanguageSettings,
      ShardedTargetList shardedTargets,
      boolean useIdeCompileOutputGroup) {

    Function<Integer, String> progressMessage =
        count ->
            String.format(
                "Building IDE resolve files for shard %s of %s...",
                count, shardedTargets.shardedTargets.size());
    Function<List<TargetExpression>, BuildResult> invocation =
        targets ->
            useIdeCompileOutputGroup
                ? doCompileIdeArtifacts(
                    project,
                    context,
                    workspaceRoot,
                    projectViewSet,
                    blazeVersionData,
                    workspaceLanguageSettings,
                    targets)
                : doResolveIdeArtifacts(
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
      BlazeVersionData blazeVersionData,
      WorkspaceLanguageSettings workspaceLanguageSettings,
      List<TargetExpression> targets) {
    try (BuildResultHelper buildResultHelper =
        BuildResultHelper.forFiles(getGenfilePrefetchFilter())) {

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
                      BlazeInvocationContext.Sync,
                      null));

      // Request the 'intellij-resolve' aspect output group.
      AspectStrategyProvider.findAspectStrategy(blazeVersionData)
          .addAspectAndOutputGroups(
              blazeCommandBuilder, OutputGroup.RESOLVE, workspaceLanguageSettings.activeLanguages);

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
        prefetchGenfiles(context, buildResultHelper.getBuildArtifacts());
      } else {
        buildResultHelper.close();
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
                    BlazeInvocationContext.Sync,
                    null));

    AspectStrategyProvider.findAspectStrategy(blazeVersionData)
        .addAspectAndOutputGroups(
            blazeCommandBuilder, OutputGroup.COMPILE, workspaceLanguageSettings.activeLanguages);

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
    return buildSystemProvider.getSyncBinaryPath();
  }
}
