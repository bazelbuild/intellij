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
package com.google.idea.blaze.base.sync;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.idea.blaze.base.BlazeIntegrationTestCase;
import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.io.WorkspaceScanner;
import com.google.idea.blaze.base.model.RuleMap;
import com.google.idea.blaze.base.model.SyncState;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.parser.ProjectViewParser;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.ErrorCollector;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.Blaze.BuildSystem;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.sync.aspects.BlazeIdeInterface;
import com.google.idea.blaze.base.sync.projectstructure.ModuleEditorImpl;
import com.google.idea.blaze.base.sync.projectstructure.ModuleEditorProvider;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.base.sync.workspace.WorkingSet;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolverImpl;
import com.google.idea.blaze.base.vcs.BlazeVcsHandler;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.TempDirTestFixture;
import com.intellij.testFramework.fixtures.impl.LightTempDirTestFixtureImpl;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/** Sets up mocks required for integration tests of the blaze sync process. */
public abstract class BlazeSyncIntegrationTestCase extends BlazeIntegrationTestCase {

  // root directory for all files outside the project directory.
  protected TempDirTestFixture tempDirectoryHandler;
  protected VirtualFile tempDirectory;

  // blaze-info data
  private static final String EXECUTION_ROOT = "/execroot/root";
  private static final String BLAZE_BIN =
      EXECUTION_ROOT + "/blaze-out/gcc-4.X.Y-crosstool-v17-hybrid-grtev3-k8-fastbuild/bin";
  private static final String BLAZE_GENFILES =
      EXECUTION_ROOT + "/blaze-out/gcc-4.X.Y-crosstool-v17-hybrid-grtev3-k8-fastbuild/genfiles";

  private static final String PROJECT_DATA_DIR = "project-data-dir";

  private MockProjectViewManager projectViewManager;
  private MockBlazeVcsHandler vcsHandler;
  private MockBlazeInfo blazeInfoData;
  private MockBlazeIdeInterface blazeIdeInterface;

  protected ErrorCollector errorCollector;
  protected BlazeContext context;

  @Override
  protected void doSetup() throws IOException {
    // Set up a workspace root outside of the tracked temp file system.
    tempDirectoryHandler = new LightTempDirTestFixtureImpl();
    tempDirectory = tempDirectoryHandler.getFile("");
    workspaceRoot = new WorkspaceRoot(new File(tempDirectory.getPath()));
    setBlazeImportSettings(
        new BlazeImportSettings(
            workspaceRoot.toString(),
            "test-project",
            workspaceRoot + "/" + PROJECT_DATA_DIR,
            "location-hash",
            workspaceRoot + "/project-view-file",
            BuildSystem.Blaze));

    projectViewManager = new MockProjectViewManager();
    vcsHandler = new MockBlazeVcsHandler();
    blazeInfoData = new MockBlazeInfo();
    blazeIdeInterface = new MockBlazeIdeInterface();
    registerProjectService(ProjectViewManager.class, projectViewManager);
    registerExtension(BlazeVcsHandler.EP_NAME, vcsHandler);
    registerApplicationService(WorkspaceScanner.class, (workspaceRoot, workspacePath) -> true);
    registerApplicationService(BlazeInfo.class, blazeInfoData);
    registerApplicationService(BlazeIdeInterface.class, blazeIdeInterface);
    registerApplicationService(
        ModuleEditorProvider.class,
        new ModuleEditorProvider() {
          @Override
          public ModuleEditorImpl getModuleEditor(
              Project project, BlazeImportSettings importSettings) {
            return new ModuleEditorImpl(project, importSettings) {
              @Override
              public void commit() {
                // don't commit module changes,
                // but make sure they're properly disposed when the test is finished
                for (ModifiableRootModel model : modifiableModels) {
                  Disposer.register(myTestRootDisposable, model::dispose);
                }
              }
            };
          }
        });

    errorCollector = new ErrorCollector();
    context = new BlazeContext();
    context.addOutputSink(IssueOutput.class, errorCollector);

    tempDirectoryHandler.findOrCreateDir(PROJECT_DATA_DIR + "/.blaze/modules");

    setBlazeInfoResults(
        ImmutableMap.of(
            BlazeInfo.blazeBinKey(Blaze.getBuildSystem(getProject())),
            BLAZE_BIN,
            BlazeInfo.blazeGenfilesKey(Blaze.getBuildSystem(getProject())),
            BLAZE_GENFILES,
            BlazeInfo.EXECUTION_ROOT_KEY,
            EXECUTION_ROOT,
            BlazeInfo.PACKAGE_PATH_KEY,
            workspaceRoot.toString()));
  }

  @Override
  protected void doTearDown() throws Exception {
    if (tempDirectoryHandler != null) {
      tempDirectoryHandler.tearDown();
    }
    super.doTearDown();
  }

  protected VirtualFile createWorkspaceFile(String relativePath, @Nullable String... contents) {
    try {
      String content = contents != null ? Joiner.on("\n").join(contents) : "";
      return tempDirectoryHandler.createFile(relativePath, content);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  protected void assertNoErrors() {
    errorCollector.assertNoIssues();
  }

  protected ArtifactLocation sourceRoot(String relativePath) {
    return ArtifactLocation.builder()
        .setRootPath(workspaceRoot.toString())
        .setRelativePath(relativePath)
        .setIsSource(true)
        .build();
  }

  protected void setProjectView(String... contents) {
    ProjectViewParser projectViewParser =
        new ProjectViewParser(context, new WorkspacePathResolverImpl(workspaceRoot));
    projectViewParser.parseProjectView(Joiner.on("\n").join(contents));

    ProjectViewSet result = projectViewParser.getResult();
    assertThat(result.getProjectViewFiles()).isNotEmpty();
    assertNoErrors();
    setProjectViewSet(result);
  }

  protected void setProjectViewSet(ProjectViewSet projectViewSet) {
    projectViewManager.projectViewSet = projectViewSet;
  }

  protected void setRuleMap(RuleMap ruleMap) {
    blazeIdeInterface.ruleMap = ruleMap;
  }

  protected void setBlazeInfoResults(Map<String, String> blazeInfoResults) {
    blazeInfoData.setResults(blazeInfoResults);
  }

  protected void runBlazeSync(BlazeSyncParams syncParams) {
    Project project = getProject();
    final BlazeSyncTask syncTask =
        new BlazeSyncTask(
            project,
            BlazeImportSettingsManager.getInstance(project).getImportSettings(),
            syncParams);
    syncTask.syncProject(context);
  }

  private static class MockProjectViewManager extends ProjectViewManager {

    private ProjectViewSet projectViewSet;

    @Nullable
    @Override
    public ProjectViewSet getProjectViewSet() {
      return projectViewSet;
    }

    @Nullable
    @Override
    public ProjectViewSet reloadProjectView(
        BlazeContext context, WorkspacePathResolver workspacePathResolver) {
      return getProjectViewSet();
    }
  }

  private static class MockBlazeVcsHandler implements BlazeVcsHandler {

    private List<WorkspacePath> addedFiles = Lists.newArrayList();

    @Override
    public String getVcsName() {
      return "Mock";
    }

    @Override
    public boolean handlesProject(BuildSystem buildSystem, WorkspaceRoot workspaceRoot) {
      return true;
    }

    @Override
    public ListenableFuture<WorkingSet> getWorkingSet(
        Project project,
        BlazeContext context,
        WorkspaceRoot workspaceRoot,
        ListeningExecutorService executor) {
      WorkingSet workingSet =
          new WorkingSet(ImmutableList.copyOf(addedFiles), ImmutableList.of(), ImmutableList.of());
      return Futures.immediateFuture(workingSet);
    }

    @Nullable
    @Override
    public BlazeVcsSyncHandler createSyncHandler(Project project, WorkspaceRoot workspaceRoot) {
      return null;
    }
  }

  private static class MockBlazeInfo extends BlazeInfo {
    private final Map<String, String> results = Maps.newHashMap();

    @Override
    public ListenableFuture<String> runBlazeInfo(
        @Nullable BlazeContext context,
        BuildSystem buildSystem,
        WorkspaceRoot workspaceRoot,
        List<String> blazeFlags,
        String key) {
      return Futures.immediateFuture(results.get(key));
    }

    @Override
    public ListenableFuture<byte[]> runBlazeInfoGetBytes(
        @Nullable BlazeContext context,
        BuildSystem buildSystem,
        WorkspaceRoot workspaceRoot,
        List<String> blazeFlags,
        String key) {
      return Futures.immediateFuture(null);
    }

    @Override
    public ListenableFuture<ImmutableMap<String, String>> runBlazeInfo(
        @Nullable BlazeContext context,
        BuildSystem buildSystem,
        WorkspaceRoot workspaceRoot,
        List<String> blazeFlags) {
      return Futures.immediateFuture(ImmutableMap.copyOf(results));
    }

    public void setResults(Map<String, String> results) {
      this.results.clear();
      this.results.putAll(results);
    }
  }

  private static class MockBlazeIdeInterface implements BlazeIdeInterface {
    private RuleMap ruleMap = new RuleMap(ImmutableMap.of());

    @Override
    public IdeResult updateRuleMap(
        Project project,
        BlazeContext context,
        WorkspaceRoot workspaceRoot,
        ProjectViewSet projectViewSet,
        List<TargetExpression> targets,
        WorkspaceLanguageSettings workspaceLanguageSettings,
        ArtifactLocationDecoder artifactLocationDecoder,
        SyncState.Builder syncStateBuilder,
        @Nullable SyncState previousSyncState,
        boolean mergeWithOldState) {
      return new IdeResult(ruleMap, BuildResult.SUCCESS);
    }

    @Override
    public BuildResult resolveIdeArtifacts(
        Project project,
        BlazeContext context,
        WorkspaceRoot workspaceRoot,
        ProjectViewSet projectViewSet,
        List<TargetExpression> targets) {
      return BuildResult.SUCCESS;
    }
  }
}
