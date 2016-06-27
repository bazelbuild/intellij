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
import com.google.idea.blaze.base.async.FutureUtil;
import com.google.idea.blaze.base.async.executor.BlazeExecutor;
import com.google.idea.blaze.base.async.process.ExternalTask;
import com.google.idea.blaze.base.async.process.LineProcessingOutputStream;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.command.ExperimentalShowArtifactsLineProcessor;
import com.google.idea.blaze.base.experiments.BoolExperiment;
import com.google.idea.blaze.base.ideinfo.RuleIdeInfo;
import com.google.idea.blaze.base.issueparser.IssueOutputLineProcessor;
import com.google.idea.blaze.base.metrics.Action;
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
import com.google.idea.blaze.base.sync.filediff.FileDiffService;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.repackaged.devtools.build.lib.ideinfo.androidstudio.AndroidStudioIdeInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;

import javax.annotation.Nullable;
import java.io.File;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Implementation of BlazeIdeInterface based on aspects.
 */
public class BlazeIdeInterfaceAspectsImpl extends BlazeIdeInterface {

  private static final Logger LOG = Logger.getInstance(BlazeIdeInterfaceAspectsImpl.class);
  private static final Label ANDROID_SDK_TARGET = new Label("//third_party/java/android/android_sdk_linux:android");
  private static final FileDiffService fileDiffService = new FileDiffService();
  private static final BoolExperiment USE_SKYLARK_ASPECT = new BoolExperiment("use.skylark.aspect", false);

  static class State implements Serializable {
    private static final long serialVersionUID = 10L;
    ImmutableMap<Label, RuleIdeInfo> ruleMap;
    File androidPlatformDirectory;
    FileDiffService.State fileState = null;
    Map<File, Label> fileToLabel = Maps.newHashMap();
    WorkspaceLanguageSettings workspaceLanguageSettings;
    String aspectStrategyName;
  }

  @Nullable
  @Override
  public IdeResult updateBlazeIdeState(Project project,
                                       BlazeContext context,
                                       WorkspaceRoot workspaceRoot,
                                       ProjectViewSet projectViewSet,
                                       List<TargetExpression> targets,
                                       WorkspaceLanguageSettings workspaceLanguageSettings,
                                       ArtifactLocationDecoder artifactLocationDecoder,
                                       SyncState.Builder syncStateBuilder,
                                       @Nullable SyncState previousSyncState,
                                       boolean requiresAndroidSdk) {
    State prevState = previousSyncState != null ? previousSyncState.get(State.class) : null;

    // If the language filter has changed, redo everything from scratch
    if (prevState != null && !prevState.workspaceLanguageSettings.equals(workspaceLanguageSettings)) {
      prevState = null;
    }

    // If the aspect strategy has changed, redo everything from scratch
    final AspectStrategy aspectStrategy = getAspectStrategy();
    if (prevState != null && !Objects.equal(prevState.aspectStrategyName, aspectStrategy.getName())) {
      prevState = null;
    }

    List<File> fileList = getIdeInfo(project, context, workspaceRoot, projectViewSet, targets, aspectStrategy, requiresAndroidSdk);
    if (!context.shouldContinue()) {
      return null;
    }

    List<File> updatedFiles = Lists.newArrayList();
    List<File> removedFiles = Lists.newArrayList();
    FileDiffService.State fileState = fileDiffService.updateFiles(
      prevState != null ? prevState.fileState : null,
      fileList,
      updatedFiles,
      removedFiles
    );
    if (fileState == null) {
      return null;
    }

    context.output(new PrintOutput(String.format(
      "Total rules: %d, new/changed: %d, removed: %d",
      fileList.size(),
      updatedFiles.size(),
      removedFiles.size()
    )));

    ListenableFuture<?> prefetchFuture = PrefetchService.getInstance().prefetchFiles(updatedFiles, true);
    if (!FutureUtil.waitForFuture(context, prefetchFuture)
      .timed("FetchAspectOutput")
      .run()
      .success()) {
      return null;
    }

    State state = updateState(
      context,
      prevState,
      fileState,
      workspaceLanguageSettings,
      artifactLocationDecoder,
      aspectStrategy,
      updatedFiles,
      removedFiles
    );
    if (state == null) {
      return null;
    }
    if (state.androidPlatformDirectory == null && requiresAndroidSdk) {
      LOG.error("Android platform directory not found.");
      return null;
    }
    syncStateBuilder.put(State.class, state);
    return new IdeResult(state.ruleMap, state.androidPlatformDirectory);
  }

  private static List<File> getIdeInfo(Project project,
                                       BlazeContext parentContext,
                                       WorkspaceRoot workspaceRoot,
                                       ProjectViewSet projectViewSet,
                                       List<TargetExpression> targets,
                                       AspectStrategy aspectStrategy,
                                       boolean addAndroidSdkTarget) {
    return Scope.push(parentContext, context -> {
      context.push(new TimingScope("ExecuteBlazeCommand"));

      List<File> result = Lists.newArrayList();

      BuildSystem buildSystem = Blaze.getBuildSystem(project);
      BlazeCommand.Builder blazeCommandBuilder = BlazeCommand.builder(buildSystem, BlazeCommandName.BUILD);
      if (addAndroidSdkTarget) {
        blazeCommandBuilder.addTargets(ANDROID_SDK_TARGET);
      }
      blazeCommandBuilder
        .addTargets(targets)
        .addBlazeFlags(BlazeFlags.EXPERIMENTAL_SHOW_ARTIFACTS)
        .addBlazeFlags(BlazeFlags.buildFlags(project, projectViewSet));

      aspectStrategy.modifyIdeInfoCommand(blazeCommandBuilder);

      int retVal = ExternalTask.builder(workspaceRoot, blazeCommandBuilder.build())
        .context(context)
        .stderr(LineProcessingOutputStream.of(
          new ExperimentalShowArtifactsLineProcessor(result, aspectStrategy.getAspectOutputFileExtension()),
          new IssueOutputLineProcessor(project, context, workspaceRoot)
        ))
        .build()
        .run(new LoggedTimingScope(project, Action.BLAZE_BUILD));

      if (retVal != 0) {
        context.setHasError();
      }

      return result;
    });
  }

  private static class RuleIdeInfoOrSdkInfo {
    public File file;
    public RuleIdeInfo ruleIdeInfo;
    public File androidPlatformDirectory;
  }

  @Nullable
  static State updateState(BlazeContext parentContext,
                           @Nullable State prevState,
                           FileDiffService.State fileState,
                           WorkspaceLanguageSettings workspaceLanguageSettings,
                           ArtifactLocationDecoder artifactLocationDecoder,
                           AspectStrategy aspectStrategy,
                           List<File> newFiles,
                           List<File> removedFiles) {
    Result<State> result = Scope.push(parentContext, (ScopedFunction<Result<State>>)context -> {
      context.push(new TimingScope("UpdateRuleMap"));

      State state = new State();
      state.fileState = fileState;
      state.workspaceLanguageSettings = workspaceLanguageSettings;
      state.aspectStrategyName = aspectStrategy.getName();

      Map<Label, RuleIdeInfo> ruleMap = Maps.newHashMap();
      Map<Label, RuleIdeInfo> updatedRules = Maps.newHashMap();
      if (prevState != null) {
        ruleMap.putAll(prevState.ruleMap);
        state.androidPlatformDirectory = prevState.androidPlatformDirectory;
        state.fileToLabel.putAll(prevState.fileToLabel);
      }

      // Update removed
      for (File removedFile : removedFiles) {
        Label label = state.fileToLabel.remove(removedFile);
        if (label != null) {
          ruleMap.remove(label);
        }
      }

      AtomicLong totalSizeLoaded = new AtomicLong(0);

      // Read protos from any new files
      List<ListenableFuture<RuleIdeInfoOrSdkInfo>> futures = Lists.newArrayList();
      for (File file : newFiles) {
        futures.add(submit(() -> {
          RuleIdeInfoOrSdkInfo ruleIdeInfoOrSdkInfo = new RuleIdeInfoOrSdkInfo();
          ruleIdeInfoOrSdkInfo.file = file;

          totalSizeLoaded.addAndGet(file.length());

          AndroidStudioIdeInfo.RuleIdeInfo ruleProto = aspectStrategy.readAspectFile(file);
          if (ruleProto.getLabel().equals(ANDROID_SDK_TARGET.toString())) {
            ruleIdeInfoOrSdkInfo.androidPlatformDirectory = getAndroidPlatformDirectoryFromAndroidTarget(
              ruleProto,
              artifactLocationDecoder
            );
          }
          else {
            ruleIdeInfoOrSdkInfo.ruleIdeInfo = IdeInfoFromProtobuf.makeRuleIdeInfo(
              workspaceLanguageSettings,
              artifactLocationDecoder,
              ruleProto
            );
          }
          return ruleIdeInfoOrSdkInfo;
        }
        ));
      }

      // Update state with result from proto files
      int duplicateRuleLabels = 0;
      try {
        for (RuleIdeInfoOrSdkInfo ruleIdeInfoOrSdkInfo : Futures.allAsList(futures).get()) {
          if (ruleIdeInfoOrSdkInfo.androidPlatformDirectory != null) {
            state.androidPlatformDirectory = ruleIdeInfoOrSdkInfo.androidPlatformDirectory;
          } else if (ruleIdeInfoOrSdkInfo.ruleIdeInfo != null) {
            File file = ruleIdeInfoOrSdkInfo.file;
            Label label = ruleIdeInfoOrSdkInfo.ruleIdeInfo.label;

            RuleIdeInfo previousRule = updatedRules.putIfAbsent(label, ruleIdeInfoOrSdkInfo.ruleIdeInfo);
            if (previousRule == null) {
              state.fileToLabel.put(file, label);
            } else {
              duplicateRuleLabels++;
            }
          }
        }
      }
      catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return Result.error(null);
      }
      catch (ExecutionException e) {
        return Result.error(e);
      }
      ruleMap.putAll(updatedRules);

      context.output(new PrintOutput(String.format(
        "Loaded %d aspect files, total size %dkB", newFiles.size(), totalSizeLoaded.get() / 1024
      )));
      if (duplicateRuleLabels > 0) {
        context.output(new PerformanceWarning(String.format(
          "There were %d duplicate rules. You may be including multiple configurations in your build. "
          + "Your IDE sync is slowed down by ~%d%%.",
          duplicateRuleLabels,
          (100 * duplicateRuleLabels / ruleMap.size())
        )));
      }

      state.ruleMap = ImmutableMap.copyOf(ruleMap);
      return Result.of(state);
    });

    if (result.error != null) {
      LOG.error(result.error);
      return null;
    }
    return result.result;
  }

  @Nullable
  private static File getAndroidPlatformDirectoryFromAndroidTarget(AndroidStudioIdeInfo.RuleIdeInfo ruleProto,
                                                                   ArtifactLocationDecoder artifactLocationDecoder) {
    if (!ruleProto.hasJavaRuleIdeInfo()) {
      return null;
    }
    AndroidStudioIdeInfo.JavaRuleIdeInfo javaRuleIdeInfo = ruleProto.getJavaRuleIdeInfo();
    if (javaRuleIdeInfo.getJarsCount() == 0) {
      return null;
    }
    AndroidStudioIdeInfo.LibraryArtifact libraryArtifact = javaRuleIdeInfo.getJars(0);
    AndroidStudioIdeInfo.ArtifactLocation artifactLocation = libraryArtifact.getJar();
    if (artifactLocation == null) {
      return null;
    }
    File androidJar = artifactLocationDecoder.decode(artifactLocation).getFile();
    return androidJar.getParentFile();
  }

  private static <T> ListenableFuture<T> submit(Callable<T> callable) {
    return BlazeExecutor.getInstance().submit(callable);
  }

  @Override
  public void resolveIdeArtifacts(Project project,
                                  BlazeContext context,
                                  WorkspaceRoot workspaceRoot,
                                  ProjectViewSet projectViewSet,
                                  List<TargetExpression> targets) {
    AspectStrategy aspectStrategy = getAspectStrategy();

    BlazeCommand.Builder blazeCommandBuilder = BlazeCommand.builder(Blaze.getBuildSystem(project), BlazeCommandName.BUILD)
      .addTargets(targets)
      .addBlazeFlags()
      .addBlazeFlags(BlazeFlags.KEEP_GOING)
      .addBlazeFlags(BlazeFlags.buildFlags(project, projectViewSet));

    aspectStrategy.modifyIdeResolveCommand(blazeCommandBuilder);

    BlazeCommand blazeCommand = blazeCommandBuilder.build();

    int retVal = ExternalTask.builder(workspaceRoot, blazeCommand)
      .context(context)
      .stderr(LineProcessingOutputStream.of(new IssueOutputLineProcessor(project, context, workspaceRoot)))
      .build()
      .run(new LoggedTimingScope(project, Action.BLAZE_BUILD));

    if (retVal != 0) {
      context.setHasError();
    }
  }

  private AspectStrategy getAspectStrategy() {
    return USE_SKYLARK_ASPECT.getValue() ? AspectStrategy.SKYLARK_ASPECT : AspectStrategy.NATIVE_ASPECT;
  }
}
