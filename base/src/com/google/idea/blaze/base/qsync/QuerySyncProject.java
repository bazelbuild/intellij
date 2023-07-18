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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.qsync.settings.QuerySyncSettings;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.sync.SyncListener;
import com.google.idea.blaze.base.sync.SyncMode;
import com.google.idea.blaze.base.sync.SyncResult;
import com.google.idea.blaze.base.sync.projectview.ImportRoots;
import com.google.idea.blaze.base.sync.projectview.LanguageSupport;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.google.idea.blaze.base.targetmaps.SourceToTargetMap;
import com.google.idea.blaze.base.util.SaveUtil;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.common.PrintOutput;
import com.google.idea.blaze.exception.BuildException;
import com.google.idea.blaze.qsync.BlazeProject;
import com.google.idea.blaze.qsync.project.BlazeProjectSnapshot;
import com.google.idea.blaze.qsync.project.PostQuerySyncData;
import com.google.idea.blaze.qsync.project.ProjectDefinition;
import com.google.idea.blaze.qsync.project.SnapshotDeserializer;
import com.google.idea.blaze.qsync.project.SnapshotSerializer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Encapsulates a loaded querysync project and it's dependencies.
 *
 * <p>This class also maintains a {@link QuerySyncProjectData} instance whose job is to expose
 * project state to the rest of the plugin and IDE.
 */
public class QuerySyncProject {

  private final Path snapshotFilePath;
  private final Project project;
  private final BlazeProject snapshotHolder;
  private final BlazeImportSettings importSettings;
  private final WorkspaceRoot workspaceRoot;
  private final ArtifactTracker artifactTracker;
  private final CustomPackageMap customPackageMap;
  private final DependencyTracker dependencyTracker;
  private final ProjectQuerier projectQuerier;
  private final ProjectDefinition projectDefinition;
  private final ProjectViewSet projectViewSet;
  private final WorkspacePathResolver workspacePathResolver;
  private final WorkspaceLanguageSettings workspaceLanguageSettings;
  private final QuerySyncSourceToTargetMap sourceToTargetMap;

  private final ProjectViewManager projectViewManager;

  private volatile QuerySyncProjectData projectData;

  public QuerySyncProject(
      Project project,
      Path snapshotFilePath,
      BlazeProject snapshotHolder,
      BlazeImportSettings importSettings,
      WorkspaceRoot workspaceRoot,
      ArtifactTracker artifactTracker,
      DependencyTracker dependencyTracker,
      ProjectQuerier projectQuerier,
      ProjectDefinition projectDefinition,
      ProjectViewSet projectViewSet,
      WorkspacePathResolver workspacePathResolver,
      WorkspaceLanguageSettings workspaceLanguageSettings,
      QuerySyncSourceToTargetMap sourceToTargetMap,
      ProjectViewManager projectViewManager) {
    this.project = project;
    this.snapshotFilePath = snapshotFilePath;
    this.snapshotHolder = snapshotHolder;
    this.importSettings = importSettings;
    this.workspaceRoot = workspaceRoot;
    this.artifactTracker = artifactTracker;
    this.dependencyTracker = dependencyTracker;
    this.projectQuerier = projectQuerier;
    this.projectDefinition = projectDefinition;
    this.projectViewSet = projectViewSet;
    this.workspacePathResolver = workspacePathResolver;
    this.workspaceLanguageSettings = workspaceLanguageSettings;
    this.sourceToTargetMap = sourceToTargetMap;
    this.projectViewManager = projectViewManager;
    projectData = new QuerySyncProjectData(workspacePathResolver, workspaceLanguageSettings);
    customPackageMap = new CustomPackageMap();
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

  public ArtifactTracker getArtifactTracker() {
    return artifactTracker;
  }

  public CustomPackageMap getCustomPackageMap() {
    return customPackageMap;
  }

  public SourceToTargetMap getSourceToTargetMap() {
    return sourceToTargetMap;
  }

  public void fullSync(BlazeContext context) {
    try {
      sync(context, Optional.empty());
    } catch (Exception e) {
      context.handleException("Project full sync failed", e);
    }
  }

  public void deltaSync(BlazeContext context) {
    try {
      syncWithCurrentSnapshot(context);
    } catch (Exception e) {
      context.handleException("Project delta sync failed", e);
    }
  }

  private void syncWithCurrentSnapshot(BlazeContext context) throws Exception {
    sync(context, snapshotHolder.getCurrent().map(BlazeProjectSnapshot::queryData));
  }

  public void sync(BlazeContext context, Optional<PostQuerySyncData> lastQuery) throws Exception {
    try {
      SaveUtil.saveAllFiles();
      BlazeProjectSnapshot newProject =
          lastQuery.isEmpty()
              ? projectQuerier.fullQuery(projectDefinition, context)
              : projectQuerier.update(projectDefinition, lastQuery.get(), context);
      customPackageMap.setCustomPackageToPackageNameMap(newProject.project());
      newProject = artifactTracker.updateSnapshot(newProject);
      onNewSnapshot(context, newProject);

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
    } finally {
      for (SyncListener syncListener : SyncListener.EP_NAME.getExtensions()) {
        // A query sync specific callback.
        syncListener.afterSync(project, context);
      }
    }
  }

  public void build(BlazeContext context, List<Path> wps) throws IOException, BuildException {
    if (getDependencyTracker().buildDependenciesForFile(context, wps)) {
      BlazeProjectSnapshot newSnapshot =
          artifactTracker.updateSnapshot(snapshotHolder.getCurrent().orElseThrow());
      onNewSnapshot(context, newSnapshot);
    }
  }

  public void buildRenderJar(BlazeContext context, List<Path> wps)
      throws IOException, BuildException {
    getDependencyTracker().buildRenderJarForFile(context, wps);
  }

  public DependencyTracker getDependencyTracker() {
    return dependencyTracker;
  }

  public void enableAnalysis(BlazeContext context, PsiFile psiFile) {
    Path path = Paths.get(psiFile.getVirtualFile().getPath());
    Path rel = workspaceRoot.path().relativize(path);
    enableAnalysis(context, ImmutableList.of(rel));
  }

  public void enableAnalysis(BlazeContext context, ImmutableList<Path> workspaceRelativePaths) {
    try {
      if (QuerySyncSettings.getInstance().syncBeforeBuild) {
        syncWithCurrentSnapshot(context);
      }
      context.output(
          PrintOutput.output(
              "Building dependencies for:\n  " + Joiner.on("\n  ").join(workspaceRelativePaths)));
      build(context, workspaceRelativePaths);
    } catch (Exception e) {
      context.handleException("Failed to build dependencies", e);
    }
  }

  public boolean canEnableAnalysisFor(Path workspacePath) {
    return !getDependencyTracker()
        .getProjectTargets(BlazeContext.create(), ImmutableList.of(workspacePath))
        .isEmpty();
  }

  public void enableRenderJar(BlazeContext context, PsiFile psiFile) {
    try {
      Path path = Paths.get(psiFile.getVirtualFile().getPath());
      String rel = workspaceRoot.path().relativize(path).toString();
      buildRenderJar(context, ImmutableList.of(WorkspacePath.createIfValid(rel).asPath()));
    } catch (Exception e) {
      context.handleException("Failed to build render jar", e);
    }
  }

  public boolean isReadyForAnalysis(PsiFile psiFile) {
    VirtualFile virtualFile = psiFile.getVirtualFile();
    if (virtualFile == null) {
      return true;
    }
    Path p = virtualFile.getFileSystem().getNioPath(virtualFile);
    if (p == null || !p.startsWith(workspaceRoot.path())) {
      // Not in the workspace.
      // p == null can occur if the file is a zip entry.
      return true;
    }
    Set<Label> pendingTargets =
        dependencyTracker.getPendingTargets(workspaceRoot.relativize(virtualFile));
    int unsynced = pendingTargets == null ? 0 : pendingTargets.size();
    return unsynced == 0;
  }

  /**
   * Reloads the project view and checks it against the stored {@link ProjectDefinition}.
   *
   * @return true if the stored {@link ProjectDefinition} matches that derived from the {@link
   *     ProjectViewSet}
   */
  public boolean isDefinitionCurrent() {
    ProjectViewSet projectViewSet =
        checkNotNull(
            projectViewManager.reloadProjectView(BlazeContext.create(), workspacePathResolver));
    ImportRoots importRoots =
        ImportRoots.builder(workspaceRoot, importSettings.getBuildSystem())
            .add(projectViewSet)
            .build();
    WorkspaceLanguageSettings workspaceLanguageSettings =
        LanguageSupport.createWorkspaceLanguageSettings(projectViewSet);
    ProjectDefinition projectDefinition =
        ProjectDefinition.create(
            importRoots.rootPaths(),
            importRoots.excludePaths(),
            LanguageClasses.translateFrom(workspaceLanguageSettings.getActiveLanguages()));

    return this.projectDefinition.equals(projectDefinition);
  }

  public Optional<PostQuerySyncData> readSnapshotFromDisk(BlazeContext context) throws IOException {
    File f = snapshotFilePath.toFile();
    if (!f.exists()) {
      return Optional.empty();
    }
    try (InputStream in = new GZIPInputStream(new FileInputStream(f))) {
      return new SnapshotDeserializer()
          .readFrom(in, context)
          .map(SnapshotDeserializer::getSyncData);
    }
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

  private void onNewSnapshot(BlazeContext context, BlazeProjectSnapshot newSnapshot)
      throws IOException {
    snapshotHolder.setCurrent(context, newSnapshot);
    projectData = projectData.withSnapshot(newSnapshot);
    writeToDisk(newSnapshot);
  }
}
