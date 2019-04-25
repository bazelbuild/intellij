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

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
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
import com.google.idea.blaze.base.command.buildresult.LocalFileOutputArtifact;
import com.google.idea.blaze.base.command.buildresult.OutputArtifact;
import com.google.idea.blaze.base.command.buildresult.RemoteOutputArtifact;
import com.google.idea.blaze.base.command.info.BlazeConfigurationHandler;
import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.google.idea.blaze.base.console.BlazeConsoleLineProcessorProvider;
import com.google.idea.blaze.base.filecache.ArtifactState;
import com.google.idea.blaze.base.filecache.ArtifactsDiff;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.lang.AdditionalLanguagesHelper;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.BlazeVersionData;
import com.google.idea.blaze.base.model.SyncState;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.prefetch.FetchExecutor;
import com.google.idea.blaze.base.prefetch.PrefetchFileSource;
import com.google.idea.blaze.base.prefetch.PrefetchService;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.Result;
import com.google.idea.blaze.base.scope.Scope;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.scope.output.PerformanceWarning;
import com.google.idea.blaze.base.scope.output.PrintOutput;
import com.google.idea.blaze.base.scope.output.StatusOutput;
import com.google.idea.blaze.base.scope.scopes.TimingScope;
import com.google.idea.blaze.base.scope.scopes.TimingScope.EventType;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.SyncProjectState;
import com.google.idea.blaze.base.sync.aspects.BuildResult.Status;
import com.google.idea.blaze.base.sync.aspects.strategy.AspectStrategy;
import com.google.idea.blaze.base.sync.aspects.strategy.AspectStrategy.OutputGroup;
import com.google.idea.blaze.base.sync.projectview.ImportRoots;
import com.google.idea.blaze.base.sync.projectview.LanguageSupport;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.base.sync.sharding.ShardedTargetList;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.pom.NavigatableAdapter;
import java.io.File;
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
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.Nullable;

/** Implementation of BlazeIdeInterface based on aspects. */
public class BlazeIdeInterfaceAspectsImpl implements BlazeIdeInterface {

  private static final Logger logger = Logger.getInstance(BlazeIdeInterfaceAspectsImpl.class);

  @Override
  public BlazeBuildOutputs buildIdeArtifacts(
      Project project,
      BlazeContext context,
      WorkspaceRoot workspaceRoot,
      ProjectViewSet projectViewSet,
      BlazeInfo blazeInfo,
      ShardedTargetList shardedTargets,
      WorkspaceLanguageSettings workspaceLanguageSettings) {
    AspectStrategy aspectStrategy = AspectStrategy.getInstance(Blaze.getBuildSystem(project));
    return runBlazeBuild(
        project,
        context,
        workspaceRoot,
        projectViewSet,
        blazeInfo,
        workspaceLanguageSettings.getActiveLanguages(),
        shardedTargets,
        aspectStrategy);
  }

  @Nullable
  @Override
  public TargetMap updateTargetMap(
      Project project,
      BlazeContext context,
      WorkspaceRoot workspaceRoot,
      SyncProjectState projectState,
      BlazeBuildOutputs buildResult,
      SyncState.Builder syncStateBuilder,
      boolean mergeWithOldState,
      @Nullable BlazeProjectData oldProjectData) {
    // If there was a partial error, make a best-effort attempt to sync. Retain
    // any old state that we have in an attempt not to lose too much code.
    if (buildResult.buildResult.status == BuildResult.Status.BUILD_ERROR) {
      mergeWithOldState = true;
    }

    Predicate<String> ideInfoPredicate = AspectStrategy.ASPECT_OUTPUT_FILE_PREDICATE;
    Collection<OutputArtifact> files =
        buildResult.perOutputGroupArtifacts.entries().stream()
            .filter(e -> e.getKey().startsWith(OutputGroup.INFO.prefix))
            .map(Map.Entry::getValue)
            .filter(f -> ideInfoPredicate.test(f.getKey()))
            .distinct()
            .collect(toImmutableList());

    TargetMap oldTargetMap = oldProjectData != null ? oldProjectData.getTargetMap() : null;
    BlazeIdeInterfaceState prevState =
        oldProjectData != null
            ? oldProjectData.getSyncState().get(BlazeIdeInterfaceState.class)
            : null;
    ArtifactsDiff diff;
    try {
      diff =
          ArtifactsDiff.diffArtifacts(prevState != null ? prevState.ideInfoFileState : null, files);
    } catch (InterruptedException e) {
      throw new ProcessCanceledException(e);
    } catch (ExecutionException e) {
      IssueOutput.error("Failed to diff aspect output files: " + e).submit(context);
      return oldTargetMap;
    }

    // if we're merging with the old state, no files are removed
    int targetCount = files.size() + (mergeWithOldState ? diff.getRemovedOutputs().size() : 0);
    int removedCount = mergeWithOldState ? 0 : diff.getRemovedOutputs().size();

    context.output(
        PrintOutput.log(
            String.format(
                "Total rules: %d, new/changed: %d, removed: %d",
                targetCount, diff.getUpdatedOutputs().size(), removedCount)));

    // prefetch remote outputs
    List<ListenableFuture<?>> futures = new ArrayList<>();
    for (OutputArtifact file : diff.getUpdatedOutputs()) {
      if (file instanceof RemoteOutputArtifact) {
        futures.add(FetchExecutor.EXECUTOR.submit(((RemoteOutputArtifact) file)::prefetch));
      }
    }
    if (!futures.isEmpty()
        && !FutureUtil.waitForFuture(context, Futures.allAsList(futures))
            .timed("PrefetchRemoteAspectOutput", EventType.Prefetching)
            .withProgressMessage("Reading IDE info result...")
            .run()
            .success()) {
      return oldTargetMap;
    }

    ListenableFuture<?> prefetchFuture =
        PrefetchService.getInstance()
            .prefetchFiles(
                LocalFileOutputArtifact.getLocalOutputFiles(diff.getUpdatedOutputs()), true, false);
    if (!FutureUtil.waitForFuture(context, prefetchFuture)
        .timed("FetchAspectOutput", EventType.Prefetching)
        .withProgressMessage("Reading IDE info result...")
        .run()
        .success()) {
      return oldTargetMap;
    }

    ImportRoots importRoots =
        ImportRoots.builder(workspaceRoot, Blaze.getBuildSystem(project))
            .add(projectState.getProjectViewSet())
            .build();

    BlazeConfigurationHandler configHandler =
        new BlazeConfigurationHandler(projectState.getBlazeInfo());
    Ref<TargetMap> targetMapReference = Ref.create(oldTargetMap);
    BlazeIdeInterfaceState state =
        updateState(
            project,
            context,
            prevState,
            diff.getNewState(),
            configHandler,
            projectState.getLanguageSettings(),
            importRoots,
            diff.getUpdatedOutputs(),
            diff.getRemovedOutputs(),
            mergeWithOldState,
            targetMapReference);
    if (state == null) {
      return oldTargetMap;
    }
    // prefetch ide-resolve genfiles
    Scope.push(
        context,
        childContext -> {
          childContext.push(new TimingScope("GenfilesPrefetchBuildArtifacts", EventType.Other));
          ImmutableList<OutputArtifact> resolveOutputs =
              buildResult.perOutputGroupArtifacts.entries().stream()
                  .filter(e -> e.getKey().startsWith(OutputGroup.RESOLVE.prefix))
                  .map(Map.Entry::getValue)
                  .distinct()
                  .collect(toImmutableList());
          prefetchGenfiles(context, resolveOutputs);
        });
    syncStateBuilder.put(state);
    return targetMapReference.get();
  }

  private static BlazeBuildOutputs runBlazeBuild(
      Project project,
      BlazeContext context,
      WorkspaceRoot workspaceRoot,
      ProjectViewSet projectViewSet,
      BlazeInfo blazeInfo,
      ImmutableSet<LanguageClass> activeLanguages,
      ShardedTargetList shardedTargets,
      AspectStrategy aspectStrategy) {

    ImmutableListMultimap.Builder<String, OutputArtifact> outputs = ImmutableListMultimap.builder();
    Function<Integer, String> progressMessage =
        count ->
            String.format(
                "Building targets for shard %s of %s...",
                count, shardedTargets.shardedTargets.size());
    Function<List<TargetExpression>, BuildResult> invocation =
        targets -> {
          BlazeBuildOutputs result =
              runBuildForTargets(
                  project,
                  context,
                  workspaceRoot,
                  projectViewSet,
                  blazeInfo,
                  activeLanguages,
                  targets,
                  aspectStrategy);
          outputs.putAll(result.perOutputGroupArtifacts);
          return result.buildResult;
        };
    BuildResult result =
        shardedTargets.runShardedCommand(project, context, progressMessage, invocation);
    return new BlazeBuildOutputs(outputs.build(), result);
  }

  /**
   * Runs blaze build with the aspect's ide-info and ide-resolve output groups for a given set of
   * targets
   */
  private static BlazeBuildOutputs runBuildForTargets(
      Project project,
      BlazeContext context,
      WorkspaceRoot workspaceRoot,
      ProjectViewSet projectViewSet,
      BlazeInfo blazeInfo,
      ImmutableSet<LanguageClass> activeLanguages,
      List<TargetExpression> targets,
      AspectStrategy aspectStrategy) {
    try (BuildResultHelper buildResultHelper =
        BuildResultHelperProvider.forFilesForSync(project, blazeInfo, f -> true)) {

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

      aspectStrategy.addAspectAndOutputGroups(
          builder, ImmutableList.of(OutputGroup.INFO, OutputGroup.RESOLVE), activeLanguages);

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
        return new BlazeBuildOutputs(ImmutableListMultimap.of(), buildResult);
      }
      return Scope.push(
          context,
          childContext -> {
            try {
              childContext.push(new TimingScope("ReadingBuildOutputs", EventType.Other));
              return new BlazeBuildOutputs(
                  buildResultHelper.getPerOutputGroupArtifacts(), buildResult);
            } catch (GetArtifactsException e) {
              IssueOutput.error("Failed to get build outputs: " + e.getMessage()).submit(context);
              return new BlazeBuildOutputs(ImmutableListMultimap.of(), buildResult);
            }
          });
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
  private static BlazeIdeInterfaceState updateState(
      Project project,
      BlazeContext parentContext,
      @Nullable BlazeIdeInterfaceState prevState,
      ImmutableMap<String, ArtifactState> fileState,
      BlazeConfigurationHandler configHandler,
      WorkspaceLanguageSettings workspaceLanguageSettings,
      ImportRoots importRoots,
      List<OutputArtifact> newFiles,
      Collection<ArtifactState> removedFiles,
      boolean mergeWithOldState,
      Ref<TargetMap> targetMapReference) {
    AspectStrategy aspectStrategy = AspectStrategy.getInstance(Blaze.getBuildSystem(project));
    Result<BlazeIdeInterfaceState> result =
        Scope.push(
            parentContext,
            context -> {
              context.push(new TimingScope("UpdateTargetMap", EventType.Other));
              context.output(new StatusOutput("Updating target map"));

              Map<String, ArtifactState> nextFileState = new HashMap<>(fileState);

              // If we're not removing we have to merge the old state
              // into the new one or we'll miss file removes next time
              if (mergeWithOldState && prevState != null) {
                prevState.ideInfoFileState.forEach(nextFileState::putIfAbsent);
              }

              BlazeIdeInterfaceState.Builder state = BlazeIdeInterfaceState.builder();
              state.ideInfoFileState = ImmutableMap.copyOf(nextFileState);
              state.workspaceLanguageSettings = workspaceLanguageSettings;

              Map<TargetKey, TargetIdeInfo> targetMap = Maps.newHashMap();
              if (prevState != null && !targetMapReference.isNull()) {
                targetMap.putAll(targetMapReference.get().map());
                state.ideInfoToTargetKey.putAll(prevState.ideInfoFileToTargetKey);
              }

              // Update removed unless we're merging with the old state
              if (!mergeWithOldState) {
                for (ArtifactState removed : removedFiles) {
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
              for (OutputArtifact file : newFiles) {
                futures.add(
                    executor.submit(
                        () -> {
                          totalSizeLoaded.addAndGet(file.getLength());
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
                    OutputArtifact file = targetFilePair.file;
                    String config = file.getBlazeConfigurationString(configHandler);
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
            ImmutableList.of(OutputGroup.COMPILE),
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
  private static void prefetchGenfiles(
      BlazeContext context, ImmutableList<OutputArtifact> artifacts) {
    Predicate<String> filter = getGenfilePrefetchFilter();
    // TODO: handle prefetching for arbitrary OutputArtifacts
    ImmutableList<File> files =
        artifacts.stream()
            .filter(a -> filter.test(a.getKey()))
            .filter(o -> o instanceof LocalFileOutputArtifact)
            .map(o -> ((LocalFileOutputArtifact) o).getFile())
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

  private static String getBinaryPath(Project project) {
    BuildSystemProvider buildSystemProvider = Blaze.getBuildSystemProvider(project);
    return buildSystemProvider.getSyncBinaryPath(project);
  }
}
