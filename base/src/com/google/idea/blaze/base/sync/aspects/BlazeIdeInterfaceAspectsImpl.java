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

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.idea.blaze.base.async.FutureUtil;
import com.google.idea.blaze.base.async.executor.BlazeExecutor;
import com.google.idea.blaze.base.async.process.ExternalTask;
import com.google.idea.blaze.base.async.process.LineProcessingOutputStream;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.command.ExperimentalShowArtifactsLineProcessor;
import com.google.idea.blaze.base.filecache.FileDiffer;
import com.google.idea.blaze.base.ideinfo.RuleIdeInfo;
import com.google.idea.blaze.base.issueparser.IssueOutputLineProcessor;
import com.google.idea.blaze.base.metrics.Action;
import com.google.idea.blaze.base.model.RuleMap;
import com.google.idea.blaze.base.model.SyncState;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.prefetch.PrefetchService;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.Result;
import com.google.idea.blaze.base.scope.Scope;
import com.google.idea.blaze.base.scope.ScopedFunction;
import com.google.idea.blaze.base.scope.output.PerformanceWarning;
import com.google.idea.blaze.base.scope.output.PrintOutput;
import com.google.idea.blaze.base.scope.scopes.LoggedTimingScope;
import com.google.idea.blaze.base.scope.scopes.TimingScope;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.Blaze.BuildSystem;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.common.experiments.BoolExperiment;
import com.google.repackaged.devtools.build.lib.ideinfo.androidstudio.AndroidStudioIdeInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nullable;

/** Implementation of BlazeIdeInterface based on aspects. */
public class BlazeIdeInterfaceAspectsImpl implements BlazeIdeInterface {

  private static final Logger LOG = Logger.getInstance(BlazeIdeInterfaceAspectsImpl.class);
  private static final BoolExperiment USE_SKYLARK_ASPECT =
      new BoolExperiment("use.skylark.aspect", false);
  private static final BoolExperiment IDE_INFO_KEEP_GOING =
      new BoolExperiment("ide.info.keep.going", true);

  static class State implements Serializable {
    private static final long serialVersionUID = 13L;
    RuleMap ruleMap;
    ImmutableMap<File, Long> fileState = null;
    Map<File, Label> fileToLabel = Maps.newHashMap();
    WorkspaceLanguageSettings workspaceLanguageSettings;
    String aspectStrategyName;
  }

  @Override
  public IdeResult updateRuleMap(
      Project project,
      BlazeContext context,
      WorkspaceRoot workspaceRoot,
      ProjectViewSet projectViewSet,
      List<TargetExpression> targets,
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
    final AspectStrategy aspectStrategy = getAspectStrategy(project);
    if (prevState != null
        && !Objects.equal(prevState.aspectStrategyName, aspectStrategy.getName())) {
      prevState = null;
    }

    IdeInfoResult ideInfoResult =
        getIdeInfo(project, context, workspaceRoot, projectViewSet, targets, aspectStrategy);
    if (ideInfoResult.buildResult == BuildResult.FATAL_ERROR) {
      return new IdeResult(prevState != null ? prevState.ruleMap : null, BuildResult.FATAL_ERROR);
    }
    // If there was a partial error, make a best-effort attempt to sync. Retain
    // any old state that we have in an attempt not to lose too much code.
    if (ideInfoResult.buildResult == BuildResult.BUILD_ERROR) {
      mergeWithOldState = true;
    }

    List<File> fileList = ideInfoResult.files;
    List<File> updatedFiles = Lists.newArrayList();
    List<File> removedFiles = Lists.newArrayList();
    ImmutableMap<File, Long> fileState =
        FileDiffer.updateFiles(
            prevState != null ? prevState.fileState : null, fileList, updatedFiles, removedFiles);
    if (fileState == null) {
      return new IdeResult(prevState != null ? prevState.ruleMap : null, BuildResult.FATAL_ERROR);
    }

    context.output(
        PrintOutput.log(
            String.format(
                "Total rules: %d, new/changed: %d, removed: %d",
                fileList.size(), updatedFiles.size(), removedFiles.size())));

    ListenableFuture<?> prefetchFuture =
        PrefetchService.getInstance().prefetchFiles(project, updatedFiles);
    if (!FutureUtil.waitForFuture(context, prefetchFuture)
        .timed("FetchAspectOutput")
        .withProgressMessage("Reading IDE info result...")
        .run()
        .success()) {
      return new IdeResult(prevState != null ? prevState.ruleMap : null, BuildResult.FATAL_ERROR);
    }

    State state =
        updateState(
            context,
            prevState,
            fileState,
            workspaceLanguageSettings,
            artifactLocationDecoder,
            aspectStrategy,
            updatedFiles,
            removedFiles,
            mergeWithOldState);
    if (state == null) {
      return new IdeResult(prevState != null ? prevState.ruleMap : null, BuildResult.FATAL_ERROR);
    }
    syncStateBuilder.put(State.class, state);
    return new IdeResult(state.ruleMap, ideInfoResult.buildResult);
  }

  private static class IdeInfoResult {
    private final List<File> files;
    private final BuildResult buildResult;

    IdeInfoResult(List<File> files, BuildResult buildResult) {
      this.files = files;
      this.buildResult = buildResult;
    }
  }

  private static IdeInfoResult getIdeInfo(
      Project project,
      BlazeContext parentContext,
      WorkspaceRoot workspaceRoot,
      ProjectViewSet projectViewSet,
      List<TargetExpression> targets,
      AspectStrategy aspectStrategy) {
    return Scope.push(
        parentContext,
        context -> {
          context.push(
              new TimingScope(String.format("Execute%sCommand", Blaze.buildSystemName(project))));

          List<File> result = Lists.newArrayList();

          BuildSystem buildSystem = Blaze.getBuildSystem(project);
          BlazeCommand.Builder blazeCommandBuilder =
              BlazeCommand.builder(buildSystem, BlazeCommandName.BUILD);
          blazeCommandBuilder.addTargets(targets);
          if (IDE_INFO_KEEP_GOING.getValue()) {
            blazeCommandBuilder.addBlazeFlags(BlazeFlags.KEEP_GOING);
          }
          blazeCommandBuilder
              .addBlazeFlags(BlazeFlags.EXPERIMENTAL_SHOW_ARTIFACTS)
              .addBlazeFlags(BlazeFlags.buildFlags(project, projectViewSet));

          aspectStrategy.modifyIdeInfoCommand(blazeCommandBuilder);

          int retVal =
              ExternalTask.builder(workspaceRoot)
                  .addBlazeCommand(blazeCommandBuilder.build())
                  .context(context)
                  .stderr(
                      LineProcessingOutputStream.of(
                          new ExperimentalShowArtifactsLineProcessor(
                              result, aspectStrategy.getAspectOutputFileExtension()),
                          new IssueOutputLineProcessor(project, context, workspaceRoot)))
                  .build()
                  .run(new LoggedTimingScope(project, Action.BLAZE_BUILD));

          BuildResult buildResult = BuildResult.fromExitCode(retVal);

          // If the experiment is turned off, upgrade any build errors to fatal errors
          if (buildResult == BuildResult.BUILD_ERROR && !IDE_INFO_KEEP_GOING.getValue()) {
            buildResult = BuildResult.FATAL_ERROR;
          }

          return new IdeInfoResult(result, buildResult);
        });
  }

  private static class RuleIdeInfoPair {
    private final File file;
    private final RuleIdeInfo ruleIdeInfo;

    RuleIdeInfoPair(File file, RuleIdeInfo ruleIdeInfo) {
      this.file = file;
      this.ruleIdeInfo = ruleIdeInfo;
    }
  }

  @Nullable
  static State updateState(
      BlazeContext parentContext,
      @Nullable State prevState,
      ImmutableMap<File, Long> fileState,
      WorkspaceLanguageSettings workspaceLanguageSettings,
      ArtifactLocationDecoder artifactLocationDecoder,
      AspectStrategy aspectStrategy,
      List<File> newFiles,
      List<File> removedFiles,
      boolean mergeWithOldState) {
    Result<State> result =
        Scope.push(
            parentContext,
            (ScopedFunction<Result<State>>)
                context -> {
                  context.push(new TimingScope("UpdateRuleMap"));

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

                  Map<Label, RuleIdeInfo> ruleMap = Maps.newHashMap();
                  Map<Label, RuleIdeInfo> updatedRules = Maps.newHashMap();
                  if (prevState != null) {
                    ruleMap.putAll(prevState.ruleMap.map());
                    state.fileToLabel.putAll(prevState.fileToLabel);
                  }

                  // Update removed unless we're merging with the old state
                  if (!mergeWithOldState) {
                    for (File removedFile : removedFiles) {
                      Label label = state.fileToLabel.remove(removedFile);
                      if (label != null) {
                        ruleMap.remove(label);
                      }
                    }
                  }

                  AtomicLong totalSizeLoaded = new AtomicLong(0);

                  ListeningExecutorService executor = BlazeExecutor.getInstance().getExecutor();

                  // Read protos from any new files
                  List<ListenableFuture<RuleIdeInfoPair>> futures = Lists.newArrayList();
                  for (File file : newFiles) {
                    futures.add(
                        executor.submit(
                            () -> {
                              totalSizeLoaded.addAndGet(file.length());

                              AndroidStudioIdeInfo.RuleIdeInfo ruleProto =
                                  aspectStrategy.readAspectFile(file);
                              RuleIdeInfo ruleIdeInfo =
                                  IdeInfoFromProtobuf.makeRuleIdeInfo(
                                      workspaceLanguageSettings,
                                      artifactLocationDecoder,
                                      ruleProto);
                              return new RuleIdeInfoPair(file, ruleIdeInfo);
                            }));
                  }

                  // Update state with result from proto files
                  int duplicateRuleLabels = 0;
                  try {
                    for (RuleIdeInfoPair ruleIdeInfoOrSdkInfo : Futures.allAsList(futures).get()) {
                      if (ruleIdeInfoOrSdkInfo.ruleIdeInfo != null) {
                        File file = ruleIdeInfoOrSdkInfo.file;
                        Label label = ruleIdeInfoOrSdkInfo.ruleIdeInfo.label;
                        RuleIdeInfo previousRule =
                            updatedRules.putIfAbsent(label, ruleIdeInfoOrSdkInfo.ruleIdeInfo);
                        if (previousRule == null) {
                          state.fileToLabel.put(file, label);
                        } else {
                          duplicateRuleLabels++;
                        }
                      }
                    }
                  } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return Result.error(null);
                  } catch (ExecutionException e) {
                    return Result.error(e);
                  }
                  ruleMap.putAll(updatedRules);

                  context.output(
                      PrintOutput.log(
                          String.format(
                              "Loaded %d aspect files, total size %dkB",
                              newFiles.size(), totalSizeLoaded.get() / 1024)));
                  if (duplicateRuleLabels > 0) {
                    context.output(
                        new PerformanceWarning(
                            String.format(
                                "There were %d duplicate rules. "
                                    + "You may be including multiple configurations in your build. "
                                    + "Your IDE sync is slowed down by ~%d%%.",
                                duplicateRuleLabels,
                                (100 * duplicateRuleLabels / ruleMap.size()))));
                  }

                  state.ruleMap = new RuleMap(ImmutableMap.copyOf(ruleMap));
                  return Result.of(state);
                });

    if (result.error != null) {
      LOG.error(result.error);
      return null;
    }
    return result.result;
  }

  @Override
  public BuildResult resolveIdeArtifacts(
      Project project,
      BlazeContext context,
      WorkspaceRoot workspaceRoot,
      ProjectViewSet projectViewSet,
      List<TargetExpression> targets) {
    AspectStrategy aspectStrategy = getAspectStrategy(project);

    BlazeCommand.Builder blazeCommandBuilder =
        BlazeCommand.builder(Blaze.getBuildSystem(project), BlazeCommandName.BUILD)
            .addTargets(targets)
            .addBlazeFlags()
            .addBlazeFlags(BlazeFlags.KEEP_GOING)
            .addBlazeFlags(BlazeFlags.buildFlags(project, projectViewSet));

    aspectStrategy.modifyIdeResolveCommand(blazeCommandBuilder);

    BlazeCommand blazeCommand = blazeCommandBuilder.build();

    int retVal =
        ExternalTask.builder(workspaceRoot)
            .addBlazeCommand(blazeCommand)
            .context(context)
            .stderr(
                LineProcessingOutputStream.of(
                    new IssueOutputLineProcessor(project, context, workspaceRoot)))
            .build()
            .run(new LoggedTimingScope(project, Action.BLAZE_BUILD));

    return BuildResult.fromExitCode(retVal);
  }

  private AspectStrategy getAspectStrategy(Project project) {
    BuildSystem buildSystem = Blaze.getBuildSystem(project);
    if (buildSystem == BuildSystem.Bazel) {
      return AspectStrategy.NATIVE_ASPECT;
    }
    return USE_SKYLARK_ASPECT.getValue()
        ? AspectStrategy.SKYLARK_ASPECT
        : AspectStrategy.NATIVE_ASPECT;
  }
}
