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
package com.google.idea.blaze.base.prefetch;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.sync.projectview.ImportRoots;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/** Implementation for prefetcher. */
public class PrefetchServiceImpl implements PrefetchService {

  @Override
  public ListenableFuture<?> prefetchFiles(Project project, Collection<File> files) {
    List<ListenableFuture<?>> futures = Lists.newArrayList();
    for (Prefetcher prefetcher : Prefetcher.EP_NAME.getExtensions()) {
      futures.add(prefetcher.prefetchFiles(project, files, FetchExecutor.EXECUTOR));
    }
    return Futures.allAsList(futures);
  }

  @Override
  public ListenableFuture<?> prefetchProjectFiles(
      Project project, ProjectViewSet projectViewSet, BlazeProjectData blazeProjectData) {
    BlazeImportSettings importSettings =
        BlazeImportSettingsManager.getInstance(project).getImportSettings();
    if (importSettings == null) {
      return Futures.immediateFuture(null);
    }
    WorkspaceRoot workspaceRoot = WorkspaceRoot.fromImportSettings(importSettings);
    ImportRoots importRoots =
        ImportRoots.builder(workspaceRoot, importSettings.getBuildSystem())
            .add(projectViewSet)
            .build();

    Set<File> files = Sets.newHashSet();
    for (WorkspacePath workspacePath : importRoots.rootDirectories()) {
      files.add(workspaceRoot.fileForPath(workspacePath));
    }
    for (PrefetchFileSource fileSource : PrefetchFileSource.EP_NAME.getExtensions()) {
      fileSource.addFilesToPrefetch(project, projectViewSet, blazeProjectData, files);
    }
    return prefetchFiles(project, files);
  }
}
