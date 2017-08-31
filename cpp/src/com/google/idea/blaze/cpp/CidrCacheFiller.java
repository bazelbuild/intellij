/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.cpp;

import com.google.common.base.Stopwatch;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.idea.blaze.base.async.executor.BlazeExecutor;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.io.VirtualFileSystemProvider;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.projectview.ProjectViewTargetImportFilter;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.project.DumbModeTask;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.cidr.lang.preprocessor.OCImportGraph;
import com.jetbrains.cidr.lang.preprocessor.OCInclusionContextUtil;
import com.jetbrains.cidr.lang.workspace.OCResolveConfiguration;
import com.jetbrains.cidr.lang.workspace.OCWorkspace;
import com.jetbrains.cidr.lang.workspace.OCWorkspaceManager;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fills some cidr caches after loading/rebuilding symbol tables.
 *
 * <p>Namely, some of the code for determining the {@link OCResolveConfiguration} of files depend on
 * the {@link OCImportGraph}. If the cidr code becomes less dependent on {@link OCImportGraph} we
 * can remove this step. See upstream bug for potential freezes due to depenence on OCImportGraph
 * (https://youtrack.jetbrains.com/issue/CPP-10557).
 */
class CidrCacheFiller extends DumbModeTask {
  private static final Logger logger = Logger.getInstance(CidrCacheFiller.class);

  private final Project project;
  private final ListeningExecutorService executor;

  private CidrCacheFiller(Project project, ListeningExecutorService executor) {
    this.project = project;
    this.executor = executor;
  }

  static void prefillCaches(Project project) {
    DumbService.getInstance(project)
        .queueTask(new CidrCacheFiller(project, BlazeExecutor.getInstance().getExecutor()));
  }

  @Override
  public void performInDumbMode(ProgressIndicator indicator) {
    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    ProjectViewSet projectViewSet = ProjectViewManager.getInstance(project).getProjectViewSet();
    if (blazeProjectData == null || projectViewSet == null) {
      return;
    }
    OCWorkspace ocWorkspace = OCWorkspaceManager.getWorkspace(project);
    if (!(ocWorkspace instanceof BlazeCWorkspace)) {
      return;
    }
    Stopwatch timer = Stopwatch.createStarted();
    indicator.setText("Pre-filling C++ caches");
    indicator.setIndeterminate(false);
    indicator.setFraction(0);
    ProjectViewTargetImportFilter filter =
        new ProjectViewTargetImportFilter(
            project, WorkspaceRoot.fromProject(project), projectViewSet);
    ArtifactLocationDecoder decoder = blazeProjectData.artifactLocationDecoder;
    Set<File> cSourceFiles = new HashSet<>();
    for (TargetIdeInfo target : blazeProjectData.targetMap.targets()) {
      if (target.cIdeInfo == null || !filter.isSourceTarget(target)) {
        continue;
      }
      for (ArtifactLocation sourceLocation : target.sources) {
        cSourceFiles.add(decoder.decode(sourceLocation));
      }
    }
    int numSources = cSourceFiles.size();
    AtomicInteger numDone = new AtomicInteger();
    indicator.setText(String.format("Pre-filling C++ caches (%s files)", numSources));
    VirtualFileSystemProvider vfsProvider = VirtualFileSystemProvider.getInstance();
    List<ListenableFuture<?>> futures = new ArrayList<>();
    for (File sourceFile : cSourceFiles) {
      futures.add(
          executor.submit(
              () -> {
                VirtualFile sourceRoot = vfsProvider.getSystem().findFileByIoFile(sourceFile);
                if (sourceRoot == null
                    || OCInclusionContextUtil.isNeedToFindRoot(sourceRoot, project)) {
                  updateIndicator(numDone, indicator, numSources);
                  return;
                }
                List<? extends OCResolveConfiguration> configurations =
                    ocWorkspace.getConfigurationsForFile(sourceRoot);
                if (configurations.isEmpty()) {
                  updateIndicator(numDone, indicator, numSources);
                  return;
                }
                OCResolveConfiguration someConfiguration = configurations.get(0);
                runInReadActionWithWriteActionPriorityWithRetries(
                    () ->
                        OCImportGraph.getAllRootHeaders(someConfiguration, sourceRoot, indicator));
                updateIndicator(numDone, indicator, numSources);
              }));
    }
    try {
      Futures.allAsList(futures).get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return;
    } catch (ProcessCanceledException e) {
      return;
    } catch (ExecutionException e) {
      logger.warn(e);
    }
    logger.info(
        String.format(
            "Pre-fill C++ caches took: %s ms for %s files",
            timer.elapsed(TimeUnit.MILLISECONDS), numSources));
  }

  private static void updateIndicator(
      AtomicInteger numDone, ProgressIndicator indicator, int total) {
    int currentDone = numDone.incrementAndGet();
    indicator.setFraction((double) currentDone / total);
  }

  // Use a friendlier read action which will allow write actions to jump in while still
  // running multiple read actions in parallel.
  // This is essentially DebuggerUtilImpl#runInReadActionWithWriteActionPriorityWithRetries
  // but avoid depending on an Impl class for this simple loop.
  private static void runInReadActionWithWriteActionPriorityWithRetries(Runnable runnable) {
    while (true) {
      if (runInReadActionWithWriteActionPriority(runnable)) {
        return;
      }
    }
  }

  // In #api_171: we can just use ProgressManager#runInReadActionWithWriteActionPriority
  // instead of these two following methods.
  private static boolean runInReadActionWithWriteActionPriority(Runnable runnable) {
    boolean success = ProgressIndicatorUtils.runInReadActionWithWriteActionPriority(runnable);
    if (!success) {
      yieldToPendingWriteActions();
    }
    return success;
  }

  private static void yieldToPendingWriteActions() {
    ApplicationManager.getApplication().invokeAndWait(EmptyRunnable.INSTANCE, ModalityState.any());
  }
}
