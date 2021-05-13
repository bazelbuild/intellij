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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

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
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeCommandRunner;
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.command.BlazeInvocationContext;
import com.google.idea.blaze.base.command.BlazeInvocationContext.ContextType;
import com.google.idea.blaze.base.command.buildresult.BlazeArtifact;
import com.google.idea.blaze.base.command.buildresult.BlazeArtifact.LocalFileArtifact;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelperProvider;
import com.google.idea.blaze.base.command.buildresult.OutputArtifact;
import com.google.idea.blaze.base.command.info.BlazeConfigurationHandler;
import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.google.idea.blaze.base.console.BlazeConsoleExperimentManager;
import com.google.idea.blaze.base.console.BlazeConsoleLineProcessorProvider;
import com.google.idea.blaze.base.filecache.ArtifactState;
import com.google.idea.blaze.base.filecache.ArtifactsDiff;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.issueparser.BlazeIssueParser;
import com.google.idea.blaze.base.issueparser.IssueOutputFilter;
import com.google.idea.blaze.base.lang.AdditionalLanguagesHelper;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.BlazeVersionData;
import com.google.idea.blaze.base.model.ProjectTargetData;
import com.google.idea.blaze.base.model.RemoteOutputArtifacts;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.prefetch.PrefetchFileSource;
import com.google.idea.blaze.base.prefetch.PrefetchService;
import com.google.idea.blaze.base.prefetch.RemoteArtifactPrefetcher;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.section.sections.AutomaticallyDeriveTargetsSection;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.Result;
import com.google.idea.blaze.base.scope.Scope;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.scope.output.PerformanceWarning;
import com.google.idea.blaze.base.scope.output.PrintOutput;
import com.google.idea.blaze.base.scope.output.StatusOutput;
import com.google.idea.blaze.base.scope.scopes.BlazeConsoleScope;
import com.google.idea.blaze.base.scope.scopes.TimingScope;
import com.google.idea.blaze.base.scope.scopes.TimingScope.EventType;
import com.google.idea.blaze.base.scope.scopes.ToolWindowScope;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.BlazeBuildParams;
import com.google.idea.blaze.base.sync.SyncProjectState;
import com.google.idea.blaze.base.sync.aspects.BuildResult.Status;
import com.google.idea.blaze.base.sync.aspects.strategy.AspectStrategy;
import com.google.idea.blaze.base.sync.aspects.strategy.AspectStrategy.OutputGroup;
import com.google.idea.blaze.base.sync.projectview.ImportRoots;
import com.google.idea.blaze.base.sync.projectview.LanguageSupport;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.base.sync.sharding.ShardedTargetList;
import com.google.idea.blaze.base.toolwindow.Task;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.pom.NavigatableAdapter;
import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.Nullable;

/** Implementation of BlazeIdeInterface based on aspects. */
public class BlazeIdeInterfaceAspectsImpl implements BlazeIdeInterface {

  private static final Logger logger = Logger.getInstance(BlazeIdeInterfaceAspectsImpl.class);
  private static final BoolExperiment disableValidationActionExperiment =
      new BoolExperiment("blaze.sync.disable.valication.action", true);

  @Override
  public BlazeBuildOutputs buildIdeArtifacts(
      Project project,
      BlazeContext context,
      WorkspaceRoot workspaceRoot,
      BlazeVersionData blazeVersion,
      BlazeBuildParams buildParams,
      ProjectViewSet projectViewSet,
      BlazeInfo blazeInfo,
      ShardedTargetList shardedTargets,
      WorkspaceLanguageSettings workspaceLanguageSettings) {
    AspectStrategy aspectStrategy = AspectStrategy.getInstance(blazeVersion);
    return runBlazeBuild(
        project,
        context,
        workspaceRoot,
        buildParams,
        projectViewSet,
        blazeInfo,
        workspaceLanguageSettings.getActiveLanguages(),
        shardedTargets,
        aspectStrategy);
  }

  @Override
  @Nullable
  public ProjectTargetData updateTargetData(
      Project project,
      BlazeContext context,
      WorkspaceRoot workspaceRoot,
      SyncProjectState projectState,
      BlazeBuildOutputs buildResult,
      boolean mergeWithOldState,
      @Nullable BlazeProjectData oldProjectData) {
    TargetMapAndInterfaceState state =
        updateTargetMap(
            project,
            context,
            workspaceRoot,
            projectState,
            buildResult,
            mergeWithOldState,
            oldProjectData);
    if (state == null) {
      return null;
    }
    context.output(PrintOutput.log("Target map size: " + state.targetMap.targets().size()));

    RemoteOutputArtifacts oldRemoteOutputs = RemoteOutputArtifacts.fromProjectData(oldProjectData);
    // combine outputs map, then filter to remove out-of-date / unnecessary items
    RemoteOutputArtifacts newRemoteOutputs =
        oldRemoteOutputs
            .appendNewOutputs(getTrackedOutputs(buildResult))
            .removeUntrackedOutputs(state.targetMap, projectState.getLanguageSettings());

    return new ProjectTargetData(state.targetMap, state.state, newRemoteOutputs);
  }

  /** Returns the {@link OutputArtifact}s we want to track between syncs. */
  private static ImmutableSet<OutputArtifact> getTrackedOutputs(BlazeBuildOutputs buildOutput) {
    // don't track intellij-info.txt outputs -- they're already tracked in
    // BlazeIdeInterfaceState
    Predicate<String> pathFilter = AspectStrategy.ASPECT_OUTPUT_FILE_PREDICATE.negate();
    return buildOutput.getOutputGroupArtifacts(group -> true).stream()
        .filter(a -> pathFilter.test(a.getRelativePath()))
        .collect(toImmutableSet());
  }

  @Nullable
  private static TargetMapAndInterfaceState updateTargetMap(
      Project project,
      BlazeContext context,
      WorkspaceRoot workspaceRoot,
      SyncProjectState projectState,
      BlazeBuildOutputs buildResult,
      boolean mergeWithOldState,
      @Nullable BlazeProjectData oldProjectData) {
    // If there was a partial error, make a best-effort attempt to sync. Retain
    // any old state that we have in an attempt not to lose too much code.
    if (buildResult.buildResult.status == BuildResult.Status.BUILD_ERROR) {
      mergeWithOldState = true;
    }

    TargetMap oldTargetMap = oldProjectData != null ? oldProjectData.getTargetMap() : null;
    BlazeIdeInterfaceState prevState =
        oldProjectData != null ? oldProjectData.getTargetData().ideInterfaceState : null;

    Predicate<String> ideInfoPredicate = AspectStrategy.ASPECT_OUTPUT_FILE_PREDICATE;
    Collection<OutputArtifact> files =
        buildResult
            .getOutputGroupArtifacts(group -> group.startsWith(OutputGroup.INFO.prefix))
            .stream()
            .filter(f -> ideInfoPredicate.test(f.getKey()))
            .distinct()
            .collect(toImmutableList());

    ArtifactsDiff diff;
    try {
      diff =
          ArtifactsDiff.diffArtifacts(prevState != null ? prevState.ideInfoFileState : null, files);
    } catch (InterruptedException e) {
      throw new ProcessCanceledException(e);
    } catch (ExecutionException e) {
      IssueOutput.error("Failed to diff aspect output files: " + e).submit(context);
      return null;
    }

    // if we're merging with the old state, no files are removed
    int targetCount = files.size() + (mergeWithOldState ? diff.getRemovedOutputs().size() : 0);
    int removedCount = mergeWithOldState ? 0 : diff.getRemovedOutputs().size();

    context.output(
        PrintOutput.log(
            String.format(
                "Total rules: %d, new/changed: %d, removed: %d",
                targetCount, diff.getUpdatedOutputs().size(), removedCount)));

    ListenableFuture<?> downloadArtifactsFuture =
        RemoteArtifactPrefetcher.getInstance()
            .downloadArtifacts(
                /* projectName= */ project.getName(),
                /* outputArtifacts= */ BlazeArtifact.getRemoteArtifacts(diff.getUpdatedOutputs()));
    ListenableFuture<?> loadFilesInJvmFuture =
        RemoteArtifactPrefetcher.getInstance()
            .loadFilesInJvm(
                /* outputArtifacts= */ BlazeArtifact.getRemoteArtifacts(diff.getUpdatedOutputs()));

    if (!FutureUtil.waitForFuture(
            context, Futures.allAsList(downloadArtifactsFuture, loadFilesInJvmFuture))
        .timed("PrefetchRemoteAspectOutput", EventType.Prefetching)
        .withProgressMessage("Reading IDE info result...")
        .run()
        .success()) {
      return null;
    }

    ListenableFuture<?> fetchLocalFilesFuture =
        PrefetchService.getInstance()
            .prefetchFiles(
                /* files= */ BlazeArtifact.getLocalFiles(diff.getUpdatedOutputs()),
                /* refetchCachedFiles= */ true,
                /* fetchFileTypes= */ false);
    if (!FutureUtil.waitForFuture(context, fetchLocalFilesFuture)
        .timed("FetchAspectOutput", EventType.Prefetching)
        .withProgressMessage("Reading IDE info result...")
        .run()
        .success()) {
      return null;
    }

    ImportRoots importRoots =
        ImportRoots.builder(workspaceRoot, Blaze.getBuildSystem(project))
            .add(projectState.getProjectViewSet())
            .build();

    BlazeConfigurationHandler configHandler =
        new BlazeConfigurationHandler(projectState.getBlazeInfo());
    TargetMapAndInterfaceState state =
        updateState(
            project,
            context,
            prevState,
            diff,
            configHandler,
            projectState.getBlazeVersionData(),
            projectState.getLanguageSettings(),
            importRoots,
            mergeWithOldState,
            oldTargetMap);
    if (state == null) {
      return null;
    }
    // prefetch ide-resolve genfiles
    Scope.push(
        context,
        childContext -> {
          childContext.push(new TimingScope("GenfilesPrefetchBuildArtifacts", EventType.Other));
          ImmutableList<OutputArtifact> resolveOutputs =
              ImmutableList.copyOf(
                  buildResult.getOutputGroupArtifacts(
                      group -> group.startsWith(OutputGroup.RESOLVE.prefix)));
          prefetchGenfiles(context, resolveOutputs);
        });
    return state;
  }

  private static BlazeBuildOutputs runBlazeBuild(
      Project project,
      BlazeContext context,
      WorkspaceRoot workspaceRoot,
      BlazeBuildParams buildParams,
      ProjectViewSet projectViewSet,
      BlazeInfo blazeInfo,
      ImmutableSet<LanguageClass> activeLanguages,
      ShardedTargetList shardedTargets,
      AspectStrategy aspectStrategy) {

    final Ref<BlazeBuildOutputs> combinedResult = new Ref<>();

    boolean parallelize = buildParams.parallelizeBuilds();

    Function<Integer, String> progressMessage =
        count ->
            String.format(
                "Building targets for shard %s of %s...", count, shardedTargets.shardCount());
    BiFunction<List<? extends TargetExpression>, Integer, BuildResult> invocation =
        (targets, shard) ->
            Scope.push(
                context,
                (childContext) -> {
                  ToolWindowScope parentToolWindowScope = context.getScope(ToolWindowScope.class);
                  childContext.push(
                      new ToolWindowScope.Builder(
                              project,
                              new Task(
                                  "Build shard " + shard,
                                  Task.Type.BLAZE_SYNC,
                                  parentToolWindowScope != null
                                      ? parentToolWindowScope.getTask()
                                      : null))
                          .setIssueParsers(
                              BlazeIssueParser.defaultIssueParsers(
                                  project, workspaceRoot, ContextType.Sync))
                          .build());
                  if (BlazeConsoleExperimentManager.isBlazeConsoleV2Enabled()) {
                    childContext.push(
                        new BlazeConsoleScope.Builder(project)
                            .addConsoleFilters(
                                new IssueOutputFilter(
                                    project,
                                    WorkspaceRoot.fromProject(project),
                                    ContextType.Sync,
                                    true))
                            .setClearPreviousState(false)
                            .build());
                  }

                  BlazeBuildOutputs result =
                      runBuildForTargets(
                          project,
                          childContext,
                          workspaceRoot,
                          buildParams,
                          projectViewSet,
                          blazeInfo,
                          activeLanguages,
                          targets,
                          aspectStrategy);
                  if (!result.buildResult.outOfMemory()) {
                    combinedResult.set(
                        combinedResult.isNull()
                            ? result
                            : combinedResult.get().updateOutputs(result));
                  }
                  return result.buildResult;
                });
    BuildResult result =
        shardedTargets.runShardedCommand(
            project, context, progressMessage, invocation, parallelize);
    if (combinedResult.isNull() || result.status == Status.FATAL_ERROR) {
      return BlazeBuildOutputs.noOutputs(result);
    }
    return combinedResult.get();
  }

  /**
   * Runs blaze build with the aspect's ide-info and ide-resolve output groups for a given set of
   * targets
   */
  private static BlazeBuildOutputs runBuildForTargets(
      Project project,
      BlazeContext context,
      WorkspaceRoot workspaceRoot,
      BlazeBuildParams buildParams,
      ProjectViewSet viewSet,
      BlazeInfo blazeInfo,
      ImmutableSet<LanguageClass> activeLanguages,
      List<? extends TargetExpression> targets,
      AspectStrategy aspectStrategy) {
    boolean onlyDirectDeps =
        viewSet.getScalarValue(AutomaticallyDeriveTargetsSection.KEY).orElse(false);

    try (BuildResultHelper buildResultHelper =
        BuildResultHelperProvider.createForSync(project, blazeInfo)) {

      BlazeCommand.Builder builder =
          BlazeCommand.builder(buildParams.blazeBinaryPath(), BlazeCommandName.BUILD);
      builder
          .addTargets(targets)
          .addBlazeFlags(BlazeFlags.KEEP_GOING)
          .addBlazeFlags(buildResultHelper.getBuildFlags())
          .addBlazeFlags(
              BlazeFlags.blazeFlags(
                  project, viewSet, BlazeCommandName.BUILD, BlazeInvocationContext.SYNC_CONTEXT));
      if (disableValidationActionExperiment.getValue()) {
        builder.addBlazeFlags(BlazeFlags.DISABLE_VALIDATIONS);
      }

      aspectStrategy.addAspectAndOutputGroups(
          builder,
          ImmutableList.of(OutputGroup.INFO, OutputGroup.RESOLVE),
          activeLanguages,
          onlyDirectDeps);

      for (BlazeCommandRunner runner : BlazeCommandRunner.EP_NAME.getExtensions()) {
        if (runner.isAvailable(project)) {
          return runner.run(
              project, builder, buildParams, buildResultHelper, workspaceRoot, context);
        }
      }
      IssueOutput.error("Failed to create build: no blaze command runner found");
      return BlazeBuildOutputs.noOutputs(BuildResult.FATAL_ERROR);
    }
  }

  private static class TargetMapAndInterfaceState {
    private final TargetMap targetMap;
    private final BlazeIdeInterfaceState state;

    TargetMapAndInterfaceState(TargetMap targetMap, BlazeIdeInterfaceState state) {
      this.targetMap = targetMap;
      this.state = state;
    }
  }

  private static class TargetFilePair {
    private final OutputArtifact file;
    private final TargetIdeInfo target;

    TargetFilePair(OutputArtifact file, TargetIdeInfo target) {
      this.file = file;
      this.target = target;
    }
  }

  @Nullable
  private static TargetMapAndInterfaceState updateState(
      Project project,
      BlazeContext parentContext,
      @Nullable BlazeIdeInterfaceState prevState,
      ArtifactsDiff fileState,
      BlazeConfigurationHandler configHandler,
      BlazeVersionData versionData,
      WorkspaceLanguageSettings languageSettings,
      ImportRoots importRoots,
      boolean mergeWithOldState,
      @Nullable TargetMap oldTargetMap) {
    AspectStrategy aspectStrategy = AspectStrategy.getInstance(versionData);
    Result<TargetMapAndInterfaceState> result =
        Scope.push(
            parentContext,
            context -> {
              context.push(new TimingScope("UpdateTargetMap", EventType.Other));
              context.output(new StatusOutput("Updating target map"));

              // ideally, we'd flush through a per-build sync time parsed from BEP. For now, though
              // just set an approximate, batched sync time.
              Instant syncTime = Instant.now();
              Map<String, ArtifactState> nextFileState = new HashMap<>(fileState.getNewState());

              // If we're not removing we have to merge the old state
              // into the new one or we'll miss file removes next time
              if (mergeWithOldState && prevState != null) {
                prevState.ideInfoFileState.forEach(nextFileState::putIfAbsent);
              }

              BlazeIdeInterfaceState.Builder state = BlazeIdeInterfaceState.builder();
              state.ideInfoFileState = ImmutableMap.copyOf(nextFileState);

              Map<TargetKey, TargetIdeInfo> targetMap = Maps.newHashMap();
              if (prevState != null && oldTargetMap != null) {
                targetMap.putAll(oldTargetMap.map());
                state.ideInfoToTargetKey.putAll(prevState.ideInfoFileToTargetKey);
              }

              // Update removed unless we're merging with the old state
              if (!mergeWithOldState) {
                for (ArtifactState removed : fileState.getRemovedOutputs()) {
                  TargetKey key = state.ideInfoToTargetKey.remove(removed.getKey());
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
              for (OutputArtifact file : fileState.getUpdatedOutputs()) {
                futures.add(
                    executor.submit(
                        () -> {
                          totalSizeLoaded.addAndGet(file.getLength());
                          IntellijIdeInfo.TargetIdeInfo message =
                              aspectStrategy.readAspectFile(file);
                          TargetIdeInfo target =
                              protoToTarget(
                                  languageSettings,
                                  importRoots,
                                  message,
                                  ignoredLanguages,
                                  syncTime);
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
                    OutputArtifact file = targetFilePair.file;
                    String config = file.getConfigurationMnemonic();
                    configurations.add(config);
                    TargetKey key = targetFilePair.target.getKey();
                    if (targetMap.putIfAbsent(key, targetFilePair.target) == null) {
                      state.ideInfoToTargetKey.forcePut(file.getKey(), key);
                    } else {
                      if (!newTargets.add(key)) {
                        duplicateTargetLabels++;
                      }
                      // prioritize the default configuration over build order
                      if (Objects.equals(config, configHandler.defaultConfigurationPathComponent)) {
                        targetMap.put(key, targetFilePair.target);
                        state.ideInfoToTargetKey.forcePut(file.getKey(), key);
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
                          fileState.getUpdatedOutputs().size(), totalSizeLoaded.get() / 1024)));
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

              // remove previously synced targets which are now unsupported
              for (TargetKey key : ImmutableSet.copyOf(state.ideInfoToTargetKey.values())) {
                TargetIdeInfo target = targetMap.get(key);
                if (target != null
                    && shouldIgnoreTarget(
                        languageSettings, importRoots, target, ignoredLanguages)) {
                  state.ideInfoToTargetKey.inverse().remove(key);
                  targetMap.remove(key);
                }
              }

              // update sync time for unchanged targets
              for (String artifactKey : fileState.getNewState().keySet()) {
                TargetKey targetKey = state.ideInfoToTargetKey.get(artifactKey);
                TargetIdeInfo target = targetKey != null ? targetMap.get(targetKey) : null;
                if (target != null) {
                  targetMap.put(targetKey, target.updateSyncTime(syncTime));
                }
              }

              ignoredLanguages.retainAll(
                  LanguageSupport.availableAdditionalLanguages(
                      languageSettings.getWorkspaceType()));
              warnIgnoredLanguages(project, context, ignoredLanguages);

              return Result.of(
                  new TargetMapAndInterfaceState(
                      new TargetMap(ImmutableMap.copyOf(targetMap)), state.build()));
            });

    if (result.error != null) {
      logger.error(result.error);
      return null;
    }
    return result.result;
  }

  private static boolean shouldIgnoreTarget(
      WorkspaceLanguageSettings languageSettings,
      ImportRoots importRoots,
      TargetIdeInfo target,
      Set<LanguageClass> ignoredLanguages) {
    Kind kind = target.getKind();
    if (kind.getLanguageClasses().stream().anyMatch(languageSettings::isLanguageActive)) {
      return false;
    }
    if (importRoots.importAsSource(target.getKey().getLabel())) {
      ignoredLanguages.addAll(kind.getLanguageClasses());
    }
    return true;
  }

  @Nullable
  private static TargetIdeInfo protoToTarget(
      WorkspaceLanguageSettings languageSettings,
      ImportRoots importRoots,
      IntellijIdeInfo.TargetIdeInfo message,
      Set<LanguageClass> ignoredLanguages,
      Instant syncTime) {
    Kind kind = Kind.fromProto(message);
    if (kind == null) {
      return null;
    }
    if (kind.getLanguageClasses().stream().anyMatch(languageSettings::isLanguageActive)) {
      return TargetIdeInfo.fromProto(message, syncTime);
    }
    TargetKey key = message.hasKey() ? TargetKey.fromProto(message.getKey()) : null;
    if (key != null && importRoots.importAsSource(key.getLabel())) {
      ignoredLanguages.addAll(kind.getLanguageClasses());
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
  public BuildResult compileIdeArtifacts(
      Project project,
      BlazeContext context,
      WorkspaceRoot workspaceRoot,
      BlazeBuildParams buildParams,
      ProjectViewSet projectViewSet,
      BlazeVersionData blazeVersionData,
      WorkspaceLanguageSettings workspaceLanguageSettings,
      ShardedTargetList shardedTargets) {
    Function<Integer, String> progressMessage =
        count ->
            String.format(
                "Compiling targets for shard %s of %s...", count, shardedTargets.shardCount());
    BiFunction<List<? extends TargetExpression>, Integer, BuildResult> invocation =
        (targets, shard) ->
            doCompileIdeArtifacts(
                project,
                context,
                workspaceRoot,
                buildParams,
                projectViewSet,
                blazeVersionData,
                workspaceLanguageSettings,
                targets);
    return shardedTargets.runShardedCommand(
        project, context, progressMessage, invocation, /* parallelize= */ false);
  }

  /** Blaze build invocation requesting the 'intellij-compile' aspect output group. */
  private static BuildResult doCompileIdeArtifacts(
      Project project,
      BlazeContext context,
      WorkspaceRoot workspaceRoot,
      BlazeBuildParams buildParams,
      ProjectViewSet projectViewSet,
      BlazeVersionData blazeVersionData,
      WorkspaceLanguageSettings workspaceLanguageSettings,
      List<? extends TargetExpression> targets) {
    BlazeCommand.Builder blazeCommandBuilder =
        BlazeCommand.builder(buildParams.blazeBinaryPath(), BlazeCommandName.BUILD)
            .addTargets(targets)
            .addBlazeFlags()
            .addBlazeFlags(BlazeFlags.KEEP_GOING)
            .addBlazeFlags(
                BlazeFlags.blazeFlags(
                    project,
                    projectViewSet,
                    BlazeCommandName.BUILD,
                    BlazeInvocationContext.SYNC_CONTEXT));

    AspectStrategy.getInstance(blazeVersionData)
        .addAspectAndOutputGroups(
            blazeCommandBuilder,
            ImmutableList.of(OutputGroup.COMPILE),
            workspaceLanguageSettings.getActiveLanguages(),
            /* directDepsOnly= */ false);

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
  private static void prefetchGenfiles(
      BlazeContext context, ImmutableList<OutputArtifact> artifacts) {
    Predicate<String> filter = getGenfilePrefetchFilter();
    // TODO: handle prefetching for arbitrary OutputArtifacts
    ImmutableList<File> files =
        artifacts.stream()
            .filter(a -> filter.test(a.getKey()))
            .filter(o -> o instanceof LocalFileArtifact)
            .map(o -> ((LocalFileArtifact) o).getFile())
            .collect(toImmutableList());
    if (files.isEmpty()) {
      return;
    }
    ListenableFuture<?> prefetchFuture =
        PrefetchService.getInstance().prefetchFiles(files, false, false);
    FutureUtil.waitForFuture(context, prefetchFuture)
        .timed("PrefetchGenfiles", EventType.Prefetching)
        .withProgressMessage("Prefetching genfiles...")
        .run();
  }
}
