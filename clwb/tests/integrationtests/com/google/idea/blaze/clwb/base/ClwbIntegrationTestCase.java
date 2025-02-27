package com.google.idea.blaze.clwb.base;

import static com.google.common.truth.Truth.assertThat;
import static junit.framework.Assert.fail;

import com.google.idea.blaze.base.async.process.ExternalTask;
import com.google.idea.blaze.base.bazel.BazelVersion;
import com.google.idea.blaze.base.logging.utils.querysync.QuerySyncActionStatsScope;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.project.AutoImportProjectOpenProcessor;
import com.google.idea.blaze.base.project.ExtendableBazelProjectCreator;
import com.google.idea.blaze.base.projectview.ProjectView;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.section.sections.TextBlock;
import com.google.idea.blaze.base.projectview.section.sections.TextBlockSection;
import com.google.idea.blaze.base.qsync.QuerySyncManager;
import com.google.idea.blaze.base.qsync.QuerySyncManager.TaskOrigin;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.BlazeUserSettings;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.google.idea.blaze.base.sync.BlazeSyncParams;
import com.google.idea.blaze.base.sync.SyncMode;
import com.google.idea.blaze.base.sync.data.BlazeDataStorage;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolverImpl;
import com.google.idea.blaze.base.toolwindow.TasksToolWindowFactory;
import com.google.idea.blaze.base.wizard2.BlazeProjectCommitException;
import com.google.idea.blaze.base.wizard2.BlazeProjectImportBuilder;
import com.google.idea.blaze.base.wizard2.CreateFromScratchProjectViewOption;
import com.google.idea.blaze.base.wizard2.WorkspaceTypeData;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.toolWindow.ToolWindowHeadlessManagerImpl;
import com.google.idea.blaze.base.sync.SyncPhaseCoordinator;
import com.jetbrains.cidr.lang.CLanguageKind;
import com.jetbrains.cidr.lang.OCLanguageUtils;
import com.jetbrains.cidr.lang.workspace.OCCompilerSettings;
import com.jetbrains.cidr.lang.workspace.OCResolveConfiguration;
import com.jetbrains.cidr.lang.workspace.OCWorkspace;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public abstract class ClwbIntegrationTestCase extends HeavyPlatformTestCase {
  protected VirtualFile myProjectRoot;

  /**
   * Normalizes an absolut posix path for windows. Since tests have to be run with `MSYS_NO_PATHCONV`
   * set to true. Paths are no longer converted by the bazel test setup.
   */
  private static String normalizePath(String path) {
    if (!SystemInfo.isWindows || !path.startsWith("/")) {
      return path;
    }

    final var parts = path.substring(1).split("/");
    parts[0] = parts[0] + ":";

    return String.join("\\", parts);
  }

  /**
   * Gets the path to the test project and performs some basic checks. The path
   * is provided by `bazel_integration_test` rule in the `BIT_WORKSPACE_DIR`
   * environment variable.
   */
  private static File getTestProjectRoot() {
    final var bitWorkspaceDir = System.getenv("BIT_WORKSPACE_DIR");
    assertThat(bitWorkspaceDir).isNotNull();

    final var file = new File(normalizePath(bitWorkspaceDir));
    assertExists(file);

    return file;
  }

  /**
   * Gets the path to the bazelisk wrapper script and performs some basic checks.
   * The path is provided by `bazel_integration_test` rule in the `BIT_BAZEL_BINARY`
   * environment variable.
   */
  private static String getTestBazelPath() throws Exception {
    final var bitBazelBinary = System.getenv("BIT_BAZEL_BINARY");
    assertThat(bitBazelBinary).isNotNull();

    final var file = new File(normalizePath(bitBazelBinary));
    assertExists(file);

    final var errStream = new ByteArrayOutputStream();

    // run bazel binary in project root to avoid downloading it twice
    final var result = ExternalTask.builder(getTestProjectRoot())
        .args(file.getAbsolutePath(), "version")
        .stderr(errStream)
        .build()
        .runAsync()
        .get();

    if (result != 0) {
      fail("cannot run bazel binary: " + errStream);
    }

    return file.getAbsolutePath();
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    final var bazelBinary = getTestBazelPath();
    BlazeUserSettings.getInstance().setBazelBinaryPath(bazelBinary);

    // register the tasks toolwindow, needs to be done manually
    final var windowManager = (ToolWindowHeadlessManagerImpl) ToolWindowManager.getInstance(myProject);
    windowManager.doRegisterToolWindow(TasksToolWindowFactory.ID);

    // create the temp directory, because CLion expected it to be present but bazel does not create it
    final var tmpDir = new File(FileUtil.getTempDirectory());
    tmpDir.mkdirs();
  }

  /**
   * Very similar to {@link AutoImportProjectOpenProcessor}.
   *
   * However, it is not possible to use the auto import processor directly because it immediately runs a sync, and it
   * is not possible to perform any kind of project setup between the import and the initial sync.
   */
  @Override
  protected void setUpProject() throws Exception {
    final var rootFile = getTestProjectRoot();
    myProjectRoot = HeavyPlatformTestCase.getVirtualFile(rootFile);

    final var projectFile = new File(rootFile, BlazeDataStorage.PROJECT_DATA_SUBDIRECTORY);

    try {
      FileUtil.ensureExists(new File(projectFile, Project.DIRECTORY_STORE_FOLDER));
    } catch (IOException e) {
      fail("could not create project directory: " + e.getMessage());
    }

    final var name = rootFile.getName();
    final var builder = new BlazeProjectImportBuilder();
    final var root = new WorkspaceRoot(rootFile);

    builder.builder().setWorkspaceData(
        WorkspaceTypeData.builder()
            .setWorkspaceName(rootFile.getName())
            .setWorkspaceRoot(root)
            .setCanonicalProjectDataLocation(rootFile)
            .setFileBrowserRoot(rootFile)
            .setWorkspacePathResolver(new WorkspacePathResolverImpl(root))
            .setBuildSystem(BuildSystemName.Bazel)
            .build()
    );

    final var bazelVersion = BazelVersionRule.getBazelVersion();
    assertThat(bazelVersion).isPresent();

    final var projectViewLines = projectViewText(bazelVersion.get()).toString().split("\n");
    final var projectViewBuilder = ProjectView.builder();
    projectViewBuilder.add(TextBlockSection.of(TextBlock.of(1, projectViewLines)));
    final var projectView = projectViewBuilder.build();

    builder.builder().setProjectView(projectView);
    builder.builder().setProjectViewFile(new File(projectFile, ".bazelproject"));
    builder.builder().setProjectViewSet(ProjectViewSet.builder().add(projectView).build());
    builder.builder().setProjectViewOption(new CreateFromScratchProjectViewOption());
    builder.builder().setProjectName(name);
    builder.builder().setProjectDataDirectory(projectFile.getAbsolutePath());

    try {
      builder.builder().commit(); // set pending project settings
    } catch (BlazeProjectCommitException e) {
      fail("could not commit project: " + e.getMessage());
    }

    final var projectCreator = ExtendableBazelProjectCreator.getInstance();
    final var projectOpt = projectCreator.createProject(builder, name, projectFile.getAbsolutePath());
    if (projectOpt.isEmpty()) {
      fail("could not create project");
    }

    myProject = projectOpt.get();
    myProject.save();

    if (!builder.validate(null, myProject)) {
      fail("could not validate project");
    }

    builder.builder().commitToProject(myProject);
  }

  protected ProjectViewBuilder projectViewText(BazelVersion version) {
    final var builder = new ProjectViewBuilder();

    builder.addRootDirectory();
    builder.setDeriveTargetsFromDirectories(true);

    // required for Bazel 6 integration tests
    builder.addBuildFlag("--enable_bzlmod");

    if (version.isAtLeast(7, 0, 0)) {
      // required for external modules
      builder.addBuildFlag("--incompatible_use_plus_in_repo_names");
      // required as build and sync flag to work for both async and qsync
      builder.addSyncFlag("--incompatible_use_plus_in_repo_names");
    }

    return builder;
  }

  protected SyncOutput runSync(BlazeSyncParams params) {
    final var context = BlazeContext.create();

    final var output = new SyncOutput();
    output.install(context);

    final var future = CompletableFuture.runAsync(() -> {
      SyncPhaseCoordinator.getInstance(myProject).runSync(params, true, context);
    }, ApplicationManager.getApplication()::executeOnPooledThread);

    while (!future.isDone()) {
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
    }

    context.close();
    LOG.info(String.format("PROJECT SYNC LOG:%n%s", output.collectLog()));

    try {
      future.get();
    } catch (ExecutionException e) {
      LOG.error("sync failed", e);
    } catch (InterruptedException e) {
      LOG.error("sync was interrupted", e);
    }

    return output;
  }

  protected BlazeSyncParams.Builder defaultSyncParams() {
    return BlazeSyncParams.builder()
        .setTitle("test sync")
        .setSyncMode(SyncMode.FULL)
        .setSyncOrigin("test")
        .setAddProjectViewTargets(true);
  }

  protected boolean runQuerySync() {
    final var future = QuerySyncManager.getInstance(myProject).onStartup(QuerySyncActionStatsScope.create(getClass(), null));

    while (!future.isDone()) {
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
    }

    try {
      return future.get();
    } catch (ExecutionException e) {
      LOG.error("query sync failed", e);
    } catch (InterruptedException e) {
      LOG.error("query sync was interrupted", e);
    }

    return false;
  }

  protected boolean enableAnalysisFor(VirtualFile file) throws ExecutionException {
    final var manager = QuerySyncManager.getInstance(myProject);
    final var targets = manager.getTargetsToBuild(file).targets();

    final var future = manager.enableAnalysis(
        targets,
        QuerySyncActionStatsScope.createForFile(getClass(), null, file),
        TaskOrigin.USER_ACTION
    );

    while (!future.isDone()) {
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
    }

    try {
      return future.get();
    } catch (InterruptedException e) {
      fail("enable analysis was interrupted");
    }

    return false;
  }

  protected VirtualFile findProjectFile(String relativePath) {
    final var file = myProjectRoot.findFileByRelativePath(relativePath);
    assertThat(file).isNotNull();

    return file;
  }

  protected PsiFile findProjectPsiFile(String relativePath) {
    final var virtualFile = findProjectFile(relativePath);

    return ReadAction.compute(() -> {
      final var psiFile = PsiManager.getInstance(myProject).findFile(virtualFile);
      assertThat(psiFile).isNotNull();

      return OCLanguageUtils.tryGetOCFile(psiFile);
    });
  }

  protected OCWorkspace getWorkspace() {
    return OCWorkspace.getInstance(myProject);
  }

  protected OCResolveConfiguration findFileResolveConfiguration(String relativePath) {
    final var file = findProjectFile(relativePath);

    final var configurations = getWorkspace().getConfigurationsForFile(file);
    assertThat(configurations).hasSize(1);

    return configurations.get(0);
  }

  protected OCCompilerSettings findFileCompilerSettings(String relativePath) {
    final var file = findProjectFile(relativePath);
    final var resolveConfiguration = findFileResolveConfiguration(relativePath);

    return resolveConfiguration.getCompilerSettings(CLanguageKind.CPP, file);
  }
}
