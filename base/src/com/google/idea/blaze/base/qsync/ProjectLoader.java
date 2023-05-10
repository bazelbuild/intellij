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

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.idea.blaze.base.bazel.BuildSystem;
import com.google.idea.blaze.base.bazel.BuildSystemProvider;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.qsync.cache.ArtifactFetcher;
import com.google.idea.blaze.base.qsync.cache.ArtifactTracker;
import com.google.idea.blaze.base.qsync.cache.FileApiArtifactFetcher;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettings.ProjectType;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.settings.BuildBinaryType;
import com.google.idea.blaze.base.sync.data.BlazeDataStorage;
import com.google.idea.blaze.base.sync.projectview.ImportRoots;
import com.google.idea.blaze.base.sync.projectview.LanguageSupport;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolverImpl;
import com.google.idea.blaze.common.PrintOutput;
import com.google.idea.blaze.qsync.BlazeProject;
import com.google.idea.blaze.qsync.PackageStatementParser;
import com.google.idea.blaze.qsync.ParallelPackageReader;
import com.google.idea.blaze.qsync.ProjectRefresher;
import com.google.idea.blaze.qsync.WorkspaceResolvingPackageReader;
import com.google.idea.blaze.qsync.project.PostQuerySyncData;
import com.google.idea.blaze.qsync.project.ProjectDefinition;
import com.google.idea.blaze.qsync.project.SnapshotDeserializer;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.AppExecutorUtil;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Optional;
import java.util.zip.GZIPInputStream;
import org.jetbrains.annotations.Nullable;

/**
 * Loads a project, either from saved state or from a {@code .blazeproject} file, yielding a {@link
 * QuerySyncProject} instance.
 *
 * <p>This class also manages injection of external (to querysync) dependencies.
 */
public class ProjectLoader {

  protected final Project project;

  public ProjectLoader(Project project) {
    this.project = project;
  }

  @Nullable
  public QuerySyncProject loadProject(BlazeContext context) throws IOException {
    BlazeImportSettings importSettings =
        Preconditions.checkNotNull(
            BlazeImportSettingsManager.getInstance(project).getImportSettings());
    if (importSettings.getProjectType() != ProjectType.QUERY_SYNC) {
      context.output(
          PrintOutput.error(
              "The project uses a legacy project structure not compatible with this version of"
                  + " Android Studio. Please reimport into a newly created project. Learn more at"
                  + " go/querysync"));
      context.setHasError();
      return null;
    }

    WorkspaceRoot workspaceRoot = WorkspaceRoot.fromImportSettings(importSettings);
    // TODO we may need to get the WorkspacePathResolver from the VcsHandler, as the old sync
    // does inside ProjectStateSyncTask.computeWorkspacePathResolverAndProjectView
    // Things will probably work without that, but we should understand why the other
    // implementations of WorkspacePathResolver exists. Perhaps they are performance
    // optimizations?
    WorkspacePathResolver workspacePathResolver = new WorkspacePathResolverImpl(workspaceRoot);

    ProjectViewManager projectViewManager = ProjectViewManager.getInstance(project);
    ProjectViewSet projectViewSet =
        checkNotNull(projectViewManager.reloadProjectView(context, workspacePathResolver));
    ImportRoots importRoots =
        ImportRoots.builder(workspaceRoot, importSettings.getBuildSystem())
            .add(projectViewSet)
            .build();
    BuildSystem buildSystem =
        BuildSystemProvider.getBuildSystemProvider(importSettings.getBuildSystem())
            .getBuildSystem();
    WorkspaceLanguageSettings workspaceLanguageSettings =
        LanguageSupport.createWorkspaceLanguageSettings(projectViewSet);

    ProjectDefinition latestProjectDef =
        ProjectDefinition.create(importRoots.rootPaths(), importRoots.excludePaths());

    Path snapshotFilePath = getSnapshotFilePath(importSettings);
    Optional<PostQuerySyncData> loadedSnapshot = loadFromDisk(snapshotFilePath);
    // don't use the snapshot if the project definition has changed, since a full re-load will be
    // necessary in that case:
    loadedSnapshot =
        loadedSnapshot.filter(snapshot -> snapshot.projectDefinition().equals(latestProjectDef));

    ProjectDefinition projectDefinition =
        loadedSnapshot.map(PostQuerySyncData::projectDefinition).orElse(latestProjectDef);

    DependencyBuilder dependencyBuilder =
        createDependencyBuilder(workspaceRoot, importRoots, buildSystem);

    BlazeProject graph = new BlazeProject();
    ArtifactFetcher artifactFetcher = createArtifactFetcher(buildSystem);
    ArtifactTracker artifactTracker = new ArtifactTracker(importSettings, artifactFetcher);
    artifactTracker.initialize();
    DependencyCache dependencyCache = new DependencyCache(artifactTracker);
    DependencyTracker dependencyTracker =
        new DependencyTracker(graph, dependencyBuilder, dependencyCache);
    ProjectRefresher projectRefresher =
        new ProjectRefresher(createPackageReader(workspaceRoot), workspaceRoot.path());
    QueryRunner queryRunner = createQueryRunner(buildSystem);
    ProjectQuerier projectQuerier = createProjectQuerier(projectRefresher, queryRunner);
    ProjectUpdater projectUpdater =
        new ProjectUpdater(project, importSettings, projectViewSet, workspaceRoot);
    graph.addListener(projectUpdater);
    QuerySyncSourceToTargetMap sourceToTargetMap =
        new QuerySyncSourceToTargetMap(graph, workspaceRoot.path());


    QuerySyncProject loadedProject =
        new QuerySyncProject(
            project,
            snapshotFilePath,
            graph,
            importSettings,
            workspaceRoot,
            dependencyCache,
            dependencyTracker,
            projectQuerier,
            projectDefinition,
            projectViewSet,
            workspacePathResolver,
            workspaceLanguageSettings,
            sourceToTargetMap,
            projectViewManager);
    return loadedProject;
  }

  private static ParallelPackageReader createPackageReader(WorkspaceRoot workspaceRoot) {
    ListeningExecutorService executor =
        MoreExecutors.listeningDecorator(
            AppExecutorUtil.createBoundedApplicationPoolExecutor("ParallelPackageReader", 128));

    return new ParallelPackageReader(
        executor,
        new WorkspaceResolvingPackageReader(workspaceRoot.path(), new PackageStatementParser()));
  }

  private ProjectQuerierImpl createProjectQuerier(
      ProjectRefresher projectRefresher, QueryRunner queryRunner) {
    return new ProjectQuerierImpl(project, queryRunner, projectRefresher);
  }

  protected QueryRunner createQueryRunner(BuildSystem buildSystem) {
    return new BazelQueryRunner(project, buildSystem);
  }

  protected DependencyBuilder createDependencyBuilder(
      WorkspaceRoot workspaceRoot, ImportRoots importRoots, BuildSystem buildSystem) {
    return new BazelDependencyBuilder(project, buildSystem, importRoots, workspaceRoot);
  }

  public Optional<PostQuerySyncData> loadFromDisk(Path snapshotFilePath) throws IOException {
    File f = snapshotFilePath.toFile();
    if (!f.exists()) {
      return Optional.empty();
    }
    try (InputStream in = new GZIPInputStream(new FileInputStream(f))) {
      return Optional.of(new SnapshotDeserializer().readFrom(in).getSyncData());
    }
  }

  private Path getSnapshotFilePath(BlazeImportSettings importSettings) {
    return BlazeDataStorage.getProjectDataDir(importSettings).toPath().resolve("qsyncdata.gz");
  }

  private ArtifactFetcher createArtifactFetcher(BuildSystem buildSystem) {
    Preconditions.checkState(
        ArtifactFetcher.EP_NAME.getExtensions().length <= 1,
        "There are too many artifact fetchers");
    ArtifactFetcher defaultArtifactFetcher = new FileApiArtifactFetcher();
    BuildBinaryType buildBinaryType =
        buildSystem.getDefaultInvoker(project, BlazeContext.create()).getType();
    for (ArtifactFetcher artifactFetcher : ArtifactFetcher.EP_NAME.getExtensions()) {
      if (artifactFetcher.isEnabled(buildBinaryType)) {
        return artifactFetcher;
      }
    }
    return defaultArtifactFetcher;
  }
}
