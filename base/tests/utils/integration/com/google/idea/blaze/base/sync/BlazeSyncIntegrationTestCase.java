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
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.BlazeIntegrationTestCase;
import com.google.idea.blaze.base.MockEventLoggingService;
import com.google.idea.blaze.base.MockProjectViewManager;
import com.google.idea.blaze.base.command.info.BlazeConfigurationHandler;
import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.google.idea.blaze.base.command.info.BlazeInfoRunner;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.logging.utils.SyncStats;
import com.google.idea.blaze.base.model.BlazeVersionData;
import com.google.idea.blaze.base.model.SyncState;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.parser.ProjectViewParser;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.ErrorCollector;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.settings.BuildSystem;
import com.google.idea.blaze.base.sync.aspects.BlazeIdeInterface;
import com.google.idea.blaze.base.sync.aspects.BuildResult;
import com.google.idea.blaze.base.sync.data.BlazeDataStorage;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.base.sync.sharding.ShardedTargetList;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolverImpl;
import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModuleRootManager;
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

  private Disposable thisClassDisposable; // disposed prior to calling parent class's @After methods
  private MockProjectViewManager projectViewManager;
  private MockBlazeInfoRunner blazeInfoData;
  private MockBlazeIdeInterface blazeIdeInterface;
  private MockEventLoggingService eventLogger;
  @Nullable private ProjectModuleMocker moduleMocker; // this will be null for heavy test cases

  protected ErrorCollector errorCollector;

  @Before
  public void doSetup() throws Exception {
    thisClassDisposable = Disposer.newDisposable();
    projectViewManager = new MockProjectViewManager(getProject(), thisClassDisposable);
    new MockBlazeVcsHandler(thisClassDisposable);
    blazeInfoData = new MockBlazeInfoRunner();
    blazeIdeInterface = new MockBlazeIdeInterface();
    eventLogger = new MockEventLoggingService(thisClassDisposable);
    if (isLightTestCase()) {
      moduleMocker = new ProjectModuleMocker(getProject(), thisClassDisposable);
    }
    registerApplicationService(BlazeInfoRunner.class, blazeInfoData);
    registerApplicationService(BlazeIdeInterface.class, blazeIdeInterface);

    errorCollector = new ErrorCollector();

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
    Disposer.dispose(thisClassDisposable);
  }

  /** The workspace content entries created during sync */
  protected ImmutableList<ContentEntry> getWorkspaceContentEntries() {
    if (moduleMocker != null) {
      return moduleMocker.getWorkspaceContentEntries();
    }

    ModuleManager moduleManager = ModuleManager.getInstance(getProject());
    Module workspaceModule = moduleManager.findModuleByName(BlazeDataStorage.WORKSPACE_MODULE_NAME);
    assertThat(workspaceModule).isNotNull();

    ContentEntry[] entries = ModuleRootManager.getInstance(workspaceModule).getContentEntries();
    return ImmutableList.copyOf(entries);
  }

  /** Search the workspace module's {@link ContentEntry}s for one with the given file. */
  @Nullable
  protected ContentEntry findContentEntry(VirtualFile root) {
    for (ContentEntry entry : getWorkspaceContentEntries()) {
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
    BlazeContext context = new BlazeContext();
    context.addOutputSink(IssueOutput.class, errorCollector);
    ProjectViewParser projectViewParser =
        new ProjectViewParser(context, new WorkspacePathResolverImpl(workspaceRoot));
    projectViewParser.parseProjectView(Joiner.on("\n").join(contents));

    ProjectViewSet result = projectViewParser.getResult();
    assertThat(result.getProjectViewFiles()).isNotEmpty();
    errorCollector.assertNoIssues();
    setProjectViewSet(result);
  }

  protected void setProjectViewSet(ProjectViewSet projectViewSet) {
    projectViewManager.setProjectView(projectViewSet);
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
    BlazeContext context = new BlazeContext();
    context.addOutputSink(IssueOutput.class, errorCollector);

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
        throw new RuntimeException(e);
      }
    }
  }

  protected List<SyncStats> getSyncStats() {
    return eventLogger.getSyncStats();
  }

  private static class MockBlazeInfoRunner extends BlazeInfoRunner {
    private final Map<String, String> results = Maps.newHashMap();

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
    public ListenableFuture<String> runBlazeInfo(
        @Nullable BlazeContext context,
        String binaryPath,
        WorkspaceRoot workspaceRoot,
        List<String> blazeFlags,
        String key) {
      return Futures.immediateFuture(results.get(key));
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
        BlazeInfo blazeInfo,
        BlazeVersionData blazeVersionData,
        BlazeConfigurationHandler configHandler,
        ShardedTargetList shardedTargets,
        WorkspaceLanguageSettings workspaceLanguageSettings,
        ArtifactLocationDecoder artifactLocationDecoder,
        SyncState.Builder syncStateBuilder,
        @Nullable SyncState previousSyncState,
        boolean mergeWithOldState,
        @Nullable TargetMap oldTargetMap) {
      return new IdeResult(targetMap, BuildResult.SUCCESS);
    }

    @Override
    public BuildResult resolveIdeArtifacts(
        Project project,
        BlazeContext context,
        WorkspaceRoot workspaceRoot,
        ProjectViewSet projectViewSet,
        BlazeInfo blazeInfo,
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
}
