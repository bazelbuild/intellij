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

import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.qsync.QuerySync;
import com.google.idea.blaze.base.qsync.QuerySyncProjectDataManager;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.intellij.openapi.project.Project;
import javax.annotation.Nullable;

/** Stores a cache of blaze project data and issues any side effects when that data is updated. */
public class DelegatingBlazeProjectDataManager implements BlazeProjectDataManager {

  private final BlazeProjectDataManager delegate;

  public DelegatingBlazeProjectDataManager(Project project) {
    if (QuerySync.isEnabled()) {
      delegate = new QuerySyncProjectDataManager(project);
    } else {
      delegate = new AspectSyncProjectDataManager(project);
    }
  }

  @Override
  @Nullable
  public BlazeProjectData getBlazeProjectData() {
    return delegate.getBlazeProjectData();
  }

  @Nullable
  @Override
  public BlazeProjectData loadProject(BlazeImportSettings importSettings) {
    return delegate.loadProject(importSettings);
  }

  @Override
  public void saveProject(BlazeImportSettings importSettings, BlazeProjectData projectData) {
    delegate.saveProject(importSettings, projectData);
  }
}
