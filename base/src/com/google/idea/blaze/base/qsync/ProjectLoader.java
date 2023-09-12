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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.idea.blaze.base.bazel.BuildSystem;
import com.google.idea.blaze.base.bazel.BuildSystemProvider;
import com.google.idea.blaze.base.command.buildresult.OutputArtifact;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.section.Glob;
import com.google.idea.blaze.base.projectview.section.sections.TestSourceSection;
import com.google.idea.blaze.base.qsync.cache.ArtifactFetcher;
import com.google.idea.blaze.base.qsync.cache.ArtifactTrackerImpl;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettings.ProjectType;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.sync.data.BlazeDataStorage;
import com.google.idea.blaze.base.sync.projectview.ImportRoots;
import com.google.idea.blaze.base.sync.projectview.LanguageSupport;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolverImpl;
import com.google.idea.blaze.base.vcs.BlazeVcsHandlerProvider;
import com.google.idea.blaze.base.vcs.BlazeVcsHandlerProvider.BlazeVcsHandler;
import com.google.idea.blaze.common.PrintOutput;
import com.google.idea.blaze.qsync.BlazeProject;
import com.google.idea.blaze.qsync.PackageStatementParser;
import com.google.idea.blaze.qsync.ParallelPackageReader;
import com.google.idea.blaze.qsync.ProjectRefresher;
import com.google.idea.blaze.qsync.VcsStateDiffer;
import com.google.idea.blaze.qsync.project.ProjectDefinition;
import com.google.idea.blaze.qsync.project.ProjectPath;
import com.intellij.openapi.project.Project;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import org.jetbrains.annotations.Nullable;

/**
 * Loads a project, either from saved state or from a {@code .blazeproject} file, yielding a {@link
 * QuerySyncProject} instance.
 *
 * <p>This class also manages injection of external (to querysync) dependencies.
 */
public class ProjectLoader {

  private final ListeningExecutorService executor;
  protected final Project project;

  public ProjectLoader(ListeningExecutorService executor, Project project) {
    this.executor = executor;
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

    ImmutableSet<String> testSourceGlobs =
        projectViewSet.listItems(TestSourceSection.KEY).stream()
            .map(Glob::toString)
            .collect(ImmutableSet.toImmutableSet());

    ProjectDefinition latestProjectDef =
        ProjectDefinition.create(
            importRoots.rootPaths(),
            importRoots.excludePaths(),
            LanguageClasses.translateFrom(workspaceLanguageSettings.getActiveLanguages()),
            testSourceGlobs);

    Path snapshotFilePath = getSnapshotFilePath(importSettings);

    ImmutableSet<String> handledRules = getHandledRuleKinds();
    DependencyBuilder dependencyBuilder =
        createDependencyBuilder(workspaceRoot, latestProjectDef, buildSystem, handledRules);
    RenderJarBuilder renderJarBuilder = createRenderJarBuilder(buildSystem);

    Path ideProjectBasePath = Paths.get(checkNotNull(project.getBasePath()));
    ProjectPath.Resolver projectPathResolver =
        ProjectPath.Resolver.create(workspaceRoot.path(), ideProjectBasePath);

    BlazeProject graph = new BlazeProject();
    ArtifactFetcher<OutputArtifact> artifactFetcher = createArtifactFetcher();
    ArtifactTrackerImpl artifactTracker =
        new ArtifactTrackerImpl(
            BlazeDataStorage.getProjectDataDir(importSettings).toPath(),
            ideProjectBasePath,
            artifactFetcher,
            projectPathResolver,
            latestProjectDef);
    artifactTracker.initialize();
    DependencyTracker dependencyTracker =
        new DependencyTracker(project, graph, dependencyBuilder, renderJarBuilder, artifactTracker);
    Optional<BlazeVcsHandler> vcsHandler =
        Optional.ofNullable(BlazeVcsHandlerProvider.vcsHandlerForProject(project));
    ProjectRefresher projectRefresher =
        new ProjectRefresher(
            executor,
            createWorkspaceRelativePackageReader(),
            vcsHandler.map(BlazeVcsHandler::getVcsStateDiffer).orElse(VcsStateDiffer.NONE),
            workspaceRoot.path(),
            graph::getCurrent,
            handledRules);
    QueryRunner queryRunner = createQueryRunner(buildSystem);
    ProjectQuerier projectQuerier = createProjectQuerier(projectRefresher, queryRunner, vcsHandler);
    ProjectUpdater projectUpdater =
        new ProjectUpdater(
            project, importSettings, projectViewSet, workspaceRoot, projectPathResolver);
    graph.addListener(projectUpdater);
    QuerySyncSourceToTargetMap sourceToTargetMap =
        new QuerySyncSourceToTargetMap(graph, workspaceRoot.path());

    return new QuerySyncProject(
        project,
        snapshotFilePath,
        graph,
        importSettings,
        workspaceRoot,
        artifactTracker,
        dependencyTracker,
        projectQuerier,
        latestProjectDef,
        projectViewSet,
        workspacePathResolver,
        workspaceLanguageSettings,
        sourceToTargetMap,
        projectViewManager,
        buildSystem);
  }

  private ParallelPackageReader createWorkspaceRelativePackageReader() {
    return new ParallelPackageReader(executor, new PackageStatementParser());
  }

  private ProjectQuerierImpl createProjectQuerier(
      ProjectRefresher projectRefresher,
      QueryRunner queryRunner,
      Optional<BlazeVcsHandler> vcsHandler) {
    return new ProjectQuerierImpl(queryRunner, projectRefresher, vcsHandler);
  }

  protected QueryRunner createQueryRunner(BuildSystem buildSystem) {
    return buildSystem.createQueryRunner(project);
  }

  protected DependencyBuilder createDependencyBuilder(
      WorkspaceRoot workspaceRoot,
      ProjectDefinition projectDefinition,
      BuildSystem buildSystem,
      ImmutableSet<String> handledRuleKinds) {
    return new BazelDependencyBuilder(
        project, buildSystem, projectDefinition, workspaceRoot, handledRuleKinds);
  }

  protected RenderJarBuilder createRenderJarBuilder(BuildSystem buildSystem) {
    return new BazelRenderJarBuilder(project, buildSystem);
  }

  private Path getSnapshotFilePath(BlazeImportSettings importSettings) {
    return BlazeDataStorage.getProjectDataDir(importSettings).toPath().resolve("qsyncdata.gz");
  }

  private ArtifactFetcher<OutputArtifact> createArtifactFetcher() {
    return new DynamicallyDispatchingArtifactFetcher(
        ImmutableList.copyOf(ArtifactFetcher.EP_NAME.getExtensions()));
  }

  /**
   * Returns an {@link ImmutableSet} of rule kinds that query sync or plugin know how to resolve
   * symbols for without building. The rules query sync always builds even if they are part of the
   * project are in {@link BlazeQueryParser.ALWAYS_BUILD_RULE_KINDS}
   */
  private ImmutableSet<String> getHandledRuleKinds() {
    ImmutableSet.Builder<String> defaultRules = ImmutableSet.builder();
    for (HandledRulesProvider ep : HandledRulesProvider.EP_NAME.getExtensionList()) {
      defaultRules.addAll(ep.handledRuleKinds());
    }
    return defaultRules.build();
  }
}
