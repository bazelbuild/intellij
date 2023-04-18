/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.qsync;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.sync.SyncListener;
import com.google.idea.blaze.base.sync.SyncMode;
import com.google.idea.blaze.base.sync.SyncResult;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.google.idea.blaze.base.targetmaps.SourceToTargetMap;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.common.PrintOutput;
import com.google.idea.blaze.exception.BuildException;
import com.google.idea.blaze.qsync.BlazeProject;
import com.google.idea.blaze.qsync.project.BlazeProjectSnapshot;
import com.google.idea.blaze.qsync.project.PostQuerySyncData;
import com.google.idea.blaze.qsync.project.ProjectDefinition;
import com.google.idea.blaze.qsync.project.SnapshotSerializer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

/**
 * Encapsulates a loaded querysync project and it's dependencies.
 *
 * <p>This class also maintains a {@link QuerySyncProjectData} instance whose job is to expose
 * project state to the rest of the plugin and IDE.
 */
public class QuerySyncProject {

  private final Logger logger = Logger.getInstance(getClass());

  private final Path snapshotFilePath;
  private final Project project;
  private final BlazeProject snapshotHolder;
  private final BlazeImportSettings importSettings;
  private final WorkspaceRoot workspaceRoot;
  private final DependencyCache dependencyCache;
  private final DependencyTracker dependencyTracker;
  private final ProjectQuerier projectQuerier;
  private final ProjectDefinition projectDefinition;
  private final ProjectViewSet projectViewSet;
  private final WorkspacePathResolver workspacePathResolver;
  private final WorkspaceLanguageSettings workspaceLanguageSettings;
  private final QuerySyncSourceToTargetMap sourceToTargetMap;

  private volatile QuerySyncProjectData projectData;

  public QuerySyncProject(
      Project project,
      Path snapshotFilePath,
      BlazeProject snapshotHolder,
      BlazeImportSettings importSettings,
      WorkspaceRoot workspaceRoot,
      DependencyCache dependencyCache,
      DependencyTracker dependencyTracker,
      ProjectQuerier projectQuerier,
      ProjectDefinition projectDefinition,
      ProjectViewSet projectViewSet,
      WorkspacePathResolver workspacePathResolver,
      WorkspaceLanguageSettings workspaceLanguageSettings,
      QuerySyncSourceToTargetMap sourceToTargetMap) {
    this.project = project;
    this.snapshotFilePath = snapshotFilePath;
    this.snapshotHolder = snapshotHolder;
    this.importSettings = importSettings;
    this.workspaceRoot = workspaceRoot;
    this.dependencyCache = dependencyCache;
    this.dependencyTracker = dependencyTracker;
    this.projectQuerier = projectQuerier;
    this.projectDefinition = projectDefinition;
    this.projectViewSet = projectViewSet;
    this.workspacePathResolver = workspacePathResolver;
    this.workspaceLanguageSettings = workspaceLanguageSettings;
    this.sourceToTargetMap = sourceToTargetMap;
    projectData = new QuerySyncProjectData(workspacePathResolver, workspaceLanguageSettings);
  }

  private boolean isExceptionError(Exception e) {
    if (e instanceof BuildException) {
      return ((BuildException) e).isIdeError();
    }
    return true;
  }

  /** Log & display a message to the user when a user-initiated action fails. */
  private void onError(String description, Exception e, BlazeContext context) {
    if (isExceptionError(e)) {
      logger.error(description, e);
      context.output(PrintOutput.error(description + ": " + e.getClass().getSimpleName()));
    } else {
      logger.info(description, e);
      context.output(PrintOutput.error(description));
    }
    context.setHasError();
    if (e.getMessage() != null) {
      context.output(PrintOutput.error("Cause: " + e.getMessage()));
    }
  }

  public QuerySyncProjectData getProjectData() {
    return projectData;
  }

  public WorkspacePathResolver getWorkspacePathResolver() {
    return workspacePathResolver;
  }

  public WorkspaceLanguageSettings getWorkspaceLanguageSettings() {
    return workspaceLanguageSettings;
  }

  public DependencyCache getDependencyCache() {
    return dependencyCache;
  }

  public SourceToTargetMap getSourceToTargetMap() {
    return sourceToTargetMap;
  }

  public void fullSync(BlazeContext context) {
    sync(context, Optional.empty());
  }

  public void deltaSync(BlazeContext context) {
    sync(context, snapshotHolder.getCurrent().map(BlazeProjectSnapshot::queryData));
  }

  public void sync(BlazeContext context, Optional<PostQuerySyncData> lastQuery) {
    try {
      BlazeProjectSnapshot newProject =
          lastQuery.isEmpty()
              ? projectQuerier.fullQuery(projectDefinition, context)
              : projectQuerier.update(lastQuery.get(), context);
      snapshotHolder.setCurrent(context, newProject);
      projectData = projectData.withSnapshot(newProject);
      writeToDisk(newProject);

      // TODO: Revisit SyncListeners once we switch fully to qsync
      for (SyncListener syncListener : SyncListener.EP_NAME.getExtensions()) {
        // A callback shared between the old and query sync implementations.
        syncListener.onSyncComplete(
            project,
            context,
            importSettings,
            projectViewSet,
            ImmutableSet.of(),
            projectData,
            SyncMode.FULL,
            SyncResult.SUCCESS);
      }
    } catch (BuildException | IOException e) {
      onError("Project sync failed", e, context);
    } finally {
      for (SyncListener syncListener : SyncListener.EP_NAME.getExtensions()) {
        // A query sync specific callback.
        syncListener.afterSync(project, context);
      }
    }
  }

  public void build(BlazeContext context, List<Path> wps) throws IOException, BuildException {
    getDependencyTracker().buildDependenciesForFile(context, wps);
  }

  public DependencyTracker getDependencyTracker() {
    return dependencyTracker;
  }

  public void enableAnalysis(BlazeContext context, PsiFile psiFile) {
    try {
      Path path = Paths.get(psiFile.getVirtualFile().getPath());
      String rel = workspaceRoot.path().relativize(path).toString();
      build(context, ImmutableList.of(WorkspacePath.createIfValid(rel).asPath()));
    } catch (IOException | BuildException e) {
      onError("Failed to build dependencies", e, context);
    }
  }

  public boolean isReadyForAnalysis(PsiFile psiFile) {
    VirtualFile virtualFile = psiFile.getVirtualFile();
    if (virtualFile == null) {
      return true;
    }
    Set<Label> pendingTargets = dependencyTracker.getPendingTargets(virtualFile);
    int unsynced = pendingTargets == null ? 0 : pendingTargets.size();
    return unsynced == 0;
  }

  private void writeToDisk(BlazeProjectSnapshot snapshot) throws IOException {
    File f = snapshotFilePath.toFile();
    if (!f.getParentFile().exists()) {
      if (!f.getParentFile().mkdirs()) {
        throw new IOException("Cannot create directory " + f.getParent());
      }
    }
    try (OutputStream o = new GZIPOutputStream(new FileOutputStream(f))) {
      new SnapshotSerializer().visit(snapshot.queryData()).toProto().writeTo(o);
    }
  }
}
