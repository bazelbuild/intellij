package com.google.idea.testing.headless;

import static com.google.common.truth.Truth.assertThat;
import static com.google.idea.testing.headless.Assertions.abort;
import static com.google.idea.testing.headless.Assertions.assertPathExists;

import com.google.idea.blaze.base.async.process.ExternalTask;
import com.google.idea.blaze.base.bazel.BazelVersion;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.lang.buildfile.psi.FuncallExpression;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.project.AutoImportProjectOpenProcessor;
import com.google.idea.blaze.base.project.ExtendableBazelProjectCreator;
import com.google.idea.blaze.base.projectview.ProjectView;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.section.sections.TextBlock;
import com.google.idea.blaze.base.projectview.section.sections.TextBlockSection;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.BlazeUserSettings;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.google.idea.blaze.base.sync.BlazeSyncParams;
import com.google.idea.blaze.base.sync.SyncMode;
import com.google.idea.blaze.base.sync.SyncPhaseCoordinator;
import com.google.idea.blaze.base.sync.autosync.ProjectTargetManager.SyncStatus;
import com.google.idea.blaze.base.sync.data.BlazeDataStorage;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolverImpl;
import com.google.idea.blaze.base.syncstatus.LegacySyncStatusContributor;
import com.google.idea.blaze.base.toolwindow.TasksToolWindowFactory;
import com.google.idea.blaze.base.wizard2.BlazeProjectCommitException;
import com.google.idea.blaze.base.wizard2.BlazeProjectImportBuilder;
import com.google.idea.blaze.base.wizard2.CreateFromScratchProjectViewOption;
import com.google.idea.blaze.base.wizard2.WorkspaceTypeData;
import com.google.idea.blaze.common.Label;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.ide.impl.ProjectUtil;
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
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public abstract class HeadlessTestCase extends HeavyPlatformTestCase {

  protected Path myProjectRoot;
  protected BazelInfo myBazelInfo;

  protected static <T> T pullFuture(Future<T> future, long timeout, TimeUnit unit) {
    final var deadline = System.currentTimeMillis() + unit.toMillis(timeout);

    while (!future.isDone()) {
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();

      if (System.currentTimeMillis() > deadline) {
        abort("timeout exceeded while waiting for Future");
      }
    }

    try {
      return future.get();
    } catch (InterruptedException | CancellationException e) {
      abort("future was interrupted or cancelled");
    } catch (ExecutionException e) {
      abort("future threw an exception", e);
    }

    return null; // unreachable
  }

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
  private static Path getTestProjectRoot() {
    final var bitWorkspaceDir = System.getenv("BIT_WORKSPACE_DIR");
    assertThat(bitWorkspaceDir).isNotNull();

    final var root = Path.of(normalizePath(bitWorkspaceDir));
    assertPathExists(root);

    return root;
  }

  /**
   * Runs a executable in the current execution root (the execroot might not
   * exist yet) and returns stdout or fails the test.
   */
  private static String exec(String ... args) throws ExecutionException, InterruptedException {
    final var outStream = new ByteArrayOutputStream();
    final var errStream = new ByteArrayOutputStream();

    final var result = ExternalTask.builder(getTestProjectRoot())
        .args(args)
        .stderr(errStream)
        .stdout(outStream)
        .build()
        .runAsync()
        .get();

    if (result != 0) {
      abort(String.format("execution '%s' failed (%d): %s", String.join(" ", args), result, errStream));
    }

    return outStream.toString();
  }

  /**
   * Gets the path to the bazelisk binary and performs some basic checks.
   * The path is provided by `bazel_integration_test` rule in the `BIT_BAZEL_BINARY`
   * environment variable.
   *
   * To avoid argument passing issues on Windows caused by sh_binary a patch to
   * rules_bazel_integration test is applied to resolve to the actual bazelisk
   * binary (see https://github.com/bazelbuild/bazel/issues/17487).
   */
  private static Path getTestBazelPath() throws ExecutionException, InterruptedException {
    final var bitBazelWrapper = System.getenv("BIT_BAZEL_BINARY");
    assertThat(bitBazelWrapper).isNotNull();

    final var bitBazeliskBinary = exec(normalizePath(bitBazelWrapper));
    assertThat(bitBazeliskBinary).isNotEmpty();

    final var bazel = Path.of(bitBazeliskBinary.trim());
    assertPathExists(bazel);

    return bazel.toAbsolutePath();
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    final var bazelBinary = getTestBazelPath();
    BlazeUserSettings.getInstance().setBazelBinaryPath(bazelBinary.toString());

    myBazelInfo = BazelInfo.parse(exec(bazelBinary.toString(), "info"));

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
    myProjectRoot = getTestProjectRoot();
    final var rootFile = myProjectRoot.toFile();

    final var projectFile = new File(rootFile, BlazeDataStorage.PROJECT_DATA_SUBDIRECTORY);

    try {
      FileUtil.ensureExists(new File(projectFile, Project.DIRECTORY_STORE_FOLDER));
    } catch (IOException e) {
      abort("could not create project directory", e);
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
      abort("could not commit project", e);
    }

    final var projectCreator = ExtendableBazelProjectCreator.getInstance();
    final var projectOpt = projectCreator.createProject(builder, name, projectFile.getAbsolutePath());
    if (projectOpt.isEmpty()) {
      abort("could not create project");
    }

    myProject = projectOpt.get();
    myProject.save();

    if (!builder.validate(null, myProject)) {
      abort("could not validate project");
    }

    builder.builder().commitToProject(myProject);

    final var options = new OpenProjectTask(false, null, false, false).withProject(myProject);
    ProjectUtil.openProject(projectFile.toPath(), options);
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

    pullFuture(future, 10, TimeUnit.MINUTES);

    context.close();
    LOG.info(String.format("PROJECT SYNC LOG:%n%s", output.collectLog()));

    return output;
  }

  protected BlazeSyncParams.Builder defaultSyncParams() {
    return BlazeSyncParams.builder()
        .setTitle("test sync")
        .setSyncMode(SyncMode.FULL)
        .setSyncOrigin("test")
        .setAddProjectViewTargets(true);
  }

  protected VirtualFile findProjectFile(String relativePath) {
    final var file = HeavyPlatformTestCase.getVirtualFile(myProjectRoot.toFile()).findFileByRelativePath(relativePath);
    assertThat(file).isNotNull();

    return file;
  }

  protected PsiFile findProjectPsiFile(String relativePath) {
    final var virtualFile = findProjectFile(relativePath);

    return ReadAction.compute(() -> {
      final var psiFile = PsiManager.getInstance(myProject).findFile(virtualFile);
      assertThat(psiFile).isNotNull();

      return psiFile;
    });
  }

  protected FuncallExpression findRule(Label label) {
    final var buildFile = findProjectPsiFile(String.format("%s/BUILD", label.buildPackage()));
    assertThat(buildFile).isInstanceOf(BuildFile.class);

    final var element = ((BuildFile) buildFile).findRule(label.name());
    assertThat(element).isNotNull();

    return element;
  }

  protected SyncStatus getSyncStatus(String relativePath) {
    final var file = findProjectFile(relativePath);

    final var status = LegacySyncStatusContributor.getSyncStatus(myProject, file);
    assertThat(status).isNotNull();

    return status;
  }
}
