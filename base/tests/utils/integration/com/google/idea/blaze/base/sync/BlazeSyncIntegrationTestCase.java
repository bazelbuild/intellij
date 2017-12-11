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
import com.google.idea.blaze.base.command.info.BlazeConfigurationHandler;
import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.google.idea.blaze.base.command.info.BlazeInfoRunner;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.logging.EventLoggingService;
import com.google.idea.blaze.base.logging.utils.SyncStats;
import com.google.idea.blaze.base.model.BlazeVersionData;
import com.google.idea.blaze.base.model.SyncState;
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
import com.google.idea.blaze.base.sync.aspects.BuildResult;
import com.google.idea.blaze.base.sync.data.BlazeDataStorage;
import com.google.idea.blaze.base.sync.projectstructure.ModuleEditorImpl;
import com.google.idea.blaze.base.sync.projectstructure.ModuleEditorProvider;
import com.google.idea.blaze.base.sync.projectstructure.ModuleFinder;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.base.sync.sharding.ShardedTargetList;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.base.sync.workspace.WorkingSet;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolverImpl;
import com.google.idea.blaze.base.vcs.BlazeVcsHandler;
import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.annotation.Nullable;
import org.junit.After;
import org.junit.Before;

/** Sets up mocks required for integration tests of the blaze sync process. */
public abstract class BlazeSyncIntegrationTestCase extends BlazeIntegrationTestCase {

  // blaze-info data
  private static final String OUTPUT_BASE = "/output_base";
  private static final String EXECUTION_ROOT = "/execroot/root";
  private static final String OUTPUT_PATH = EXECUTION_ROOT + "/blaze-out";
  private static final String BLAZE_BIN =
      OUTPUT_PATH + "/gcc-4.X.Y-crosstool-v17-hybrid-grtev3-k8-fastbuild/bin";
  private static final String BLAZE_GENFILES =
      OUTPUT_PATH + "/gcc-4.X.Y-crosstool-v17-hybrid-grtev3-k8-fastbuild/genfiles";

  private MockProjectViewManager projectViewManager;
  private MockBlazeVcsHandler vcsHandler;
  private MockBlazeInfoRunner blazeInfoData;
  private MockBlazeIdeInterface blazeIdeInterface;
  private MockEventLoggingService eventLogger;

  protected ErrorCollector errorCollector;
  protected BlazeContext context;

  private ImmutableList<ContentEntry> workspaceContentEntries = ImmutableList.of();
  private Map<String, ModifiableRootModel> modules = Maps.newHashMap();
  protected Disposable moduleStructureDisposable;

  private class MockModuleEditor extends ModuleEditorImpl {
    public MockModuleEditor(Project project, BlazeImportSettings importSettings) {
      super(project, importSettings);
    }

    @Override
    public void commit() {
      // don't commit module changes,
      // and make sure they're properly disposed when the test is finished
      for (ModifiableRootModel model : modules.values()) {
        Disposer.register(moduleStructureDisposable, model::dispose);
        if (model.getModule().getName().equals(BlazeDataStorage.WORKSPACE_MODULE_NAME)) {
          workspaceContentEntries = ImmutableList.copyOf(model.getContentEntries());
        }
      }
      BlazeSyncIntegrationTestCase.this.modules = modules;
    }
  }

  // Since MockModuleEditor does not actually commit modules, the normal ModuleManager
  // won't find modules we've created. This helps look up modules for later stages of Sync.
  // We could override ModuleManager, but that has a wide interface and there are a lot of
  // changes across API versions.
  private class MockModuleFinder implements ModuleFinder {

    MockModuleFinder() {}

    @Nullable
    @Override
    public Module findModuleByName(String name) {
      return getModuleCreatedDuringSync(name);
    }
  }

  @Before
  public void doSetup() throws Exception {
    moduleStructureDisposable = Disposer.newDisposable();
    projectViewManager = new MockProjectViewManager();
    vcsHandler = new MockBlazeVcsHandler();
    blazeInfoData = new MockBlazeInfoRunner();
    blazeIdeInterface = new MockBlazeIdeInterface();
    eventLogger = new MockEventLoggingService();
    registerProjectService(ProjectViewManager.class, projectViewManager);
    registerExtension(BlazeVcsHandler.EP_NAME, vcsHandler);
    registerApplicationService(EventLoggingService.class, eventLogger);
    registerApplicationService(BlazeInfoRunner.class, blazeInfoData);
    registerApplicationService(BlazeIdeInterface.class, blazeIdeInterface);
    registerApplicationService(ModuleEditorProvider.class, MockModuleEditor::new);
    registerProjectService(ModuleFinder.class, new MockModuleFinder());

    errorCollector = new ErrorCollector();
    context = new BlazeContext();
    context.addOutputSink(IssueOutput.class, errorCollector);

    fileSystem.createDirectory(projectDataDirectory.getPath() + "/.blaze/modules");

    blazeInfoData.setResults(
        ImmutableMap.<String, String>builder()
            .put(BlazeInfo.blazeBinKey(Blaze.getBuildSystem(getProject())), BLAZE_BIN)
            .put(BlazeInfo.blazeGenfilesKey(Blaze.getBuildSystem(getProject())), BLAZE_GENFILES)
            .put(BlazeInfo.EXECUTION_ROOT_KEY, EXECUTION_ROOT)
            .put(BlazeInfo.OUTPUT_BASE_KEY, OUTPUT_BASE)
            .put(BlazeInfo.OUTPUT_PATH_KEY, OUTPUT_PATH)
            .put(BlazeInfo.PACKAGE_PATH_KEY, workspaceRoot.toString())
            .build());
  }

  @After
  public void doTearDown() {
    Disposer.dispose(moduleStructureDisposable);
  }

  /** The workspace content entries created during sync */
  protected ImmutableList<ContentEntry> getWorkspaceContentEntries() {
    return workspaceContentEntries;
  }

  /** The modules created during sync */
  private Module getModuleCreatedDuringSync(String module) {
    ModifiableRootModel modifiableRootModel = modules.get(module);
    return modifiableRootModel != null ? modifiableRootModel.getModule() : null;
  }

  /** Search the workspace module's {@link ContentEntry}s for one with the given file. */
  @Nullable
  protected ContentEntry findContentEntry(VirtualFile root) {
    for (ContentEntry entry : workspaceContentEntries) {
      if (root.equals(entry.getFile())) {
        return entry;
      }
    }
    return null;
  }

  protected static ArtifactLocation sourceRoot(String relativePath) {
    return ArtifactLocation.builder().setRelativePath(relativePath).setIsSource(true).build();
  }

  protected void setProjectView(String... contents) {
    ProjectViewParser projectViewParser =
        new ProjectViewParser(context, new WorkspacePathResolverImpl(workspaceRoot));
    projectViewParser.parseProjectView(Joiner.on("\n").join(contents));

    ProjectViewSet result = projectViewParser.getResult();
    assertThat(result.getProjectViewFiles()).isNotEmpty();
    errorCollector.assertNoIssues();
    setProjectViewSet(result);
  }

  protected void setProjectViewSet(ProjectViewSet projectViewSet) {
    projectViewManager.projectViewSet = projectViewSet;
  }

  protected void setTargetMap(TargetMap targetMap) {
    blazeIdeInterface.targetMap = targetMap;
  }

  protected void runBlazeSync(BlazeSyncParams syncParams) {
    Project project = getProject();
    final BlazeSyncTask syncTask =
        new BlazeSyncTask(
            project,
            BlazeImportSettingsManager.getInstance(project).getImportSettings(),
            syncParams);

    // We need to run sync off EDT to keep IntelliJ's transaction system happy
    // Because the sync task itself wants to run occasional EDT tasks, we'll have
    // to keep flushing the event queue.
    Future<?> future =
        Executors.newSingleThreadExecutor().submit(() -> syncTask.syncProject(context));
    while (!future.isDone()) {
      IdeEventQueue.getInstance().flushQueue();
      try {
        Thread.sleep(50);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
  }

  protected List<SyncStats> getSyncStats() {
    return eventLogger.syncStats;
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

    @Override
    public ListenableFuture<String> getUpstreamContent(
        Project project,
        BlazeContext context,
        WorkspaceRoot workspaceRoot,
        WorkspacePath path,
        ListeningExecutorService executor) {
      return Futures.immediateFuture("");
    }

    @Nullable
    @Override
    public BlazeVcsSyncHandler createSyncHandler(Project project, WorkspaceRoot workspaceRoot) {
      return null;
    }
  }

  private static class MockBlazeInfoRunner extends BlazeInfoRunner {
    private final Map<String, String> results = Maps.newHashMap();

    @Override
    public ListenableFuture<String> runBlazeInfo(
        @Nullable BlazeContext context,
        String binaryPath,
        WorkspaceRoot workspaceRoot,
        List<String> blazeFlags,
        String key) {
      return Futures.immediateFuture(results.get(key));
    }

    @Override
    public ListenableFuture<byte[]> runBlazeInfoGetBytes(
        @Nullable BlazeContext context,
        String binaryPath,
        WorkspaceRoot workspaceRoot,
        List<String> blazeFlags,
        String key) {
      return Futures.immediateFuture(null);
    }

    @Override
    public ListenableFuture<BlazeInfo> runBlazeInfo(
        @Nullable BlazeContext context,
        BuildSystem buildSystem,
        String binaryPath,
        WorkspaceRoot workspaceRoot,
        List<String> blazeFlags) {
      return Futures.immediateFuture(new BlazeInfo(buildSystem, ImmutableMap.copyOf(results)));
    }

    public void setResults(Map<String, String> results) {
      this.results.clear();
      this.results.putAll(results);
    }
  }

  private static class MockBlazeIdeInterface implements BlazeIdeInterface {
    private TargetMap targetMap = new TargetMap(ImmutableMap.of());

    @Override
    public IdeResult updateTargetMap(
        Project project,
        BlazeContext context,
        WorkspaceRoot workspaceRoot,
        ProjectViewSet projectViewSet,
        BlazeVersionData blazeVersionData,
        BlazeConfigurationHandler configHandler,
        ShardedTargetList shardedTargets,
        WorkspaceLanguageSettings workspaceLanguageSettings,
        ArtifactLocationDecoder artifactLocationDecoder,
        SyncState.Builder syncStateBuilder,
        @Nullable SyncState previousSyncState,
        boolean mergeWithOldState) {
      return new IdeResult(targetMap, BuildResult.SUCCESS);
    }

    @Override
    public BuildResult resolveIdeArtifacts(
        Project project,
        BlazeContext context,
        WorkspaceRoot workspaceRoot,
        ProjectViewSet projectViewSet,
        BlazeVersionData blazeVersionData,
        WorkspaceLanguageSettings workspaceLanguageSettings,
        ShardedTargetList shardedTargets) {
      return BuildResult.SUCCESS;
    }

    @Override
    public BuildResult compileIdeArtifacts(
        Project project,
        BlazeContext context,
        WorkspaceRoot workspaceRoot,
        ProjectViewSet projectViewSet,
        BlazeVersionData blazeVersionData,
        WorkspaceLanguageSettings workspaceLanguageSettings,
        ShardedTargetList shardedTargets) {
      return BuildResult.SUCCESS;
    }
  }

  private static class MockEventLoggingService implements EventLoggingService {

    private final List<SyncStats> syncStats = Lists.newArrayList();

    @Override
    public void log(SyncStats stats) {
      syncStats.add(stats);
    }

    @Override
    public void logEvent(Class<?> loggingClass, String eventType, Map<String, String> keyValues) {}

    @Override
    public void logEvent(
        Class<?> loggingClass,
        String eventType,
        Map<String, String> keyValues,
        @Nullable Long durationInNanos) {}
  }
}
