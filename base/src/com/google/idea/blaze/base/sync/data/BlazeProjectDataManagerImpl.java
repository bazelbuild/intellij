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
package com.google.idea.blaze.base.sync.data;

import com.google.common.collect.Lists;
import com.google.idea.blaze.base.async.executor.BlazeExecutor;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.sync.BlazeSyncPlugin;
import com.google.idea.blaze.base.util.SerializationUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.io.IOException;
import java.util.List;
import javax.annotation.Nullable;

/** Stores a cache of blaze project data and issues any side effects when that data is updated. */
public class BlazeProjectDataManagerImpl implements BlazeProjectDataManager {

  private static final Logger logger =
      Logger.getInstance(BlazeProjectDataManagerImpl.class.getName());

  private final Project project;

  @Nullable private volatile BlazeProjectData blazeProjectData;

  private final Object saveLock = new Object();

  public static BlazeProjectDataManagerImpl getImpl(Project project) {
    return (BlazeProjectDataManagerImpl) BlazeProjectDataManager.getInstance(project);
  }

  public BlazeProjectDataManagerImpl(Project project) {
    this.project = project;
  }

  @Nullable
  public BlazeProjectData loadProjectRoot(BlazeImportSettings importSettings) throws IOException {
    BlazeProjectData projectData = blazeProjectData;
    if (projectData != null) {
      return projectData;
    }
    synchronized (this) {
      projectData = blazeProjectData;
      return projectData != null ? projectData : loadProject(importSettings);
    }
  }

  @Override
  @Nullable
  public BlazeProjectData getBlazeProjectData() {
    return blazeProjectData;
  }

  @Nullable
  private synchronized BlazeProjectData loadProject(BlazeImportSettings importSettings)
      throws IOException {
    File file = getCacheFile(project, importSettings);

    List<ClassLoader> classLoaders = Lists.newArrayList();
    for (BlazeSyncPlugin syncPlugin : BlazeSyncPlugin.EP_NAME.getExtensions()) {
      classLoaders.add(syncPlugin.getClass().getClassLoader());
    }
    classLoaders.add(getClass().getClassLoader());
    classLoaders.add(Thread.currentThread().getContextClassLoader());

    blazeProjectData = (BlazeProjectData) SerializationUtil.loadFromDisk(file, classLoaders);
    return blazeProjectData;
  }

  public void saveProject(
      final BlazeImportSettings importSettings, final BlazeProjectData blazeProjectData) {
    this.blazeProjectData = blazeProjectData;

    // Can only run one save operation per project at a time
    synchronized (saveLock) {
      BlazeExecutor.submitTask(
          project,
          "Saving sync data...",
          (ProgressIndicator indicator) -> {
            try {
              File file = getCacheFile(project, importSettings);
              SerializationUtil.saveToDisk(file, blazeProjectData);
            } catch (IOException e) {
              logger.error(
                  "Could not save cache data file to disk. Please resync project. Error: "
                      + e.getMessage());
            }
          });
    }
  }

  private static File getCacheFile(Project project, BlazeImportSettings importSettings) {
    return new File(BlazeDataStorage.getProjectCacheDir(project, importSettings), "cache.dat");
  }
}
