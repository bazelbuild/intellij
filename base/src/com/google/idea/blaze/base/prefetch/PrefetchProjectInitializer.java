/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.prefetch;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManagerImpl;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbModeTask;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerAdapter;
import com.intellij.util.TimeoutUtil;
import java.io.IOException;
import javax.annotation.Nullable;

/** Run prefetching on project open, prior to initial indexing step. */
public class PrefetchProjectInitializer extends ApplicationComponent.Adapter {

  private static final Logger logger = Logger.getInstance(PrefetchProjectInitializer.class);

  private static final BoolExperiment prefetchOnProjectOpen =
      new BoolExperiment("prefetch.on.project.open2", true);

  @Override
  public void initComponent() {
    ProjectManager projectManager = ProjectManager.getInstance();
    projectManager.addProjectManagerListener(
        new ProjectManagerAdapter() {
          @Override
          public void projectOpened(Project project) {
            if (prefetchOnProjectOpen.getValue()) {
              prefetchProjectFiles(project);
            }
          }
        });
  }

  private static void prefetchProjectFiles(Project project) {
    if (!Blaze.isBlazeProject(project)) {
      return;
    }
    BlazeProjectData projectData = getBlazeProjectData(project);
    ProjectViewSet projectViewSet = ProjectViewManager.getInstance(project).getProjectViewSet();
    if (projectViewSet == null) {
      return;
    }
    long start = System.currentTimeMillis();
    ListenableFuture<?> future =
        PrefetchService.getInstance().prefetchProjectFiles(project, projectViewSet, projectData);
    TransactionGuard.submitTransaction(
        project,
        () -> {
          DumbService.getInstance(project).queueTask(new PrefetchTask(future, start));
        });
  }

  static class PrefetchTask extends DumbModeTask {
    private final ListenableFuture<?> future;
    private final long startTimeMillis;

    private PrefetchTask(ListenableFuture<?> future, long startTimeMillis) {
      this.future = future;
      this.startTimeMillis = startTimeMillis;
    }

    @Override
    public void performInDumbMode(ProgressIndicator indicator) {
      indicator.setIndeterminate(true);
      indicator.setText("Prefetching files...");
      while (!future.isCancelled() && !future.isDone()) {
        indicator.checkCanceled();
        TimeoutUtil.sleep(100);
      }
      long end = System.currentTimeMillis();
      logger.info(String.format("Initial prefetching took: %d ms", (end - startTimeMillis)));
    }
  }

  @Nullable
  private static BlazeProjectData getBlazeProjectData(Project project) {
    BlazeImportSettings importSettings =
        BlazeImportSettingsManager.getInstance(project).getImportSettings();
    if (importSettings == null) {
      return null;
    }
    try {
      return BlazeProjectDataManagerImpl.getImpl(project).loadProjectRoot(importSettings);
    } catch (IOException e) {
      // ignore: if we can't load the previous project data, we can't fetch dependencies
      logger.info("Couldn't load project data for prefetcher", e);
      return null;
    }
  }
}
