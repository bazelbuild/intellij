/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.prefetch;

import com.google.idea.blaze.base.filecache.FileCaches;
import com.google.idea.blaze.base.filecache.RemoteOutputsCache;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.Scope;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManagerImpl;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import javax.annotation.Nullable;
import org.jetbrains.ide.PooledThreadExecutor;

/** Run prefetching on project open, prior to initial indexing step. */
public class PrefetchProjectInitializer implements ApplicationComponent {

  private static final Logger logger = Logger.getInstance(PrefetchProjectInitializer.class);

  private static final BoolExperiment prefetchOnProjectOpen =
      new BoolExperiment("prefetch.on.project.open2", true);

  @Override
  public void initComponent() {
    ApplicationManager.getApplication()
        .getMessageBus()
        .connect()
        .subscribe(
            ProjectManager.TOPIC,
            new ProjectManagerListener() {
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
    ProjectViewSet projectViewSet = getProjectViewSet(project);
    PrefetchIndexingTask.submitPrefetchingTask(
        project,
        PooledThreadExecutor.INSTANCE.submit(
            () -> {
              RemoteOutputsCache.getInstance(project).initialize();
              if (projectViewSet != null) {
                FileCaches.initialize(project, projectData, projectViewSet);
              }
            }),
        "Reading local caches");

    if (projectViewSet == null) {
      return;
    }
    PrefetchIndexingTask.submitPrefetchingTask(
        project,
        PrefetchService.getInstance().prefetchProjectFiles(project, projectViewSet, projectData),
        "Initial Prefetching");
  }

  @Nullable
  private static BlazeProjectData getBlazeProjectData(Project project) {
    BlazeImportSettings importSettings =
        BlazeImportSettingsManager.getInstance(project).getImportSettings();
    if (importSettings == null) {
      return null;
    }
    BlazeProjectData blazeProjectData =
        BlazeProjectDataManagerImpl.getImpl(project).loadProjectRoot(importSettings);
    if (blazeProjectData == null) {
      logger.info("Couldn't load project data for prefetcher");
    }
    return blazeProjectData;
  }

  /** Get the cached {@link ProjectViewSet}, or reload it from source. */
  @Nullable
  private static ProjectViewSet getProjectViewSet(Project project) {
    ProjectViewSet projectViewSet = ProjectViewManager.getInstance(project).getProjectViewSet();
    if (projectViewSet != null) {
      return projectViewSet;
    }
    return Scope.root(
        context -> {
          return ProjectViewManager.getInstance(project).reloadProjectView(context);
        });
  }
}
