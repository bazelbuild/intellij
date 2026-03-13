package com.google.idea.testing.headless;

import static com.google.common.truth.Truth.assertThat;
import static com.google.idea.testing.headless.Assertions.abort;

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
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.testing.bazel.BazelProjectFixture;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
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
import org.junit.Rule;

public abstract class HeadlessTestCase extends HeavyPlatformTestCase {

  @Rule
  public BazelProjectFixture fixture = new BazelProjectFixture();

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
   * Runs a executable in the current execution root (the execroot might not
   * exist yet) and returns stdout or fails the test.
   */
  private String exec(String ... args) throws ExecutionException, InterruptedException {
    final var outStream = new ByteArrayOutputStream();
    final var errStream = new ByteArrayOutputStream();

    final var result = ExternalTask.builder(fixture.getProjectDirectory())
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

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    final var bazelBinary = fixture.getBazelExecutable();
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
    myProjectRoot = fixture.getProjectDirectory();
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

    final var bazelVersion = BazelVersion.parseVersion(fixture.getBazelVersion());

    final var projectViewLines = projectViewText(bazelVersion).toString().split("\n");
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

    // use the provided repository cache
    builder.addBuildFlag("--repository_cache=" + fixture.getRepositoryCache());
    builder.addSyncFlag("--repository_cache=" + fixture.getRepositoryCache());

    // required for external modules
    builder.addBuildFlag("--incompatible_use_plus_in_repo_names");
    builder.addSyncFlag("--incompatible_use_plus_in_repo_names");

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
    final var buildFile = findProjectPsiFile(String.format("%s/BUILD", label.blazePackage()));
    assertThat(buildFile).isInstanceOf(BuildFile.class);

    final var element = ((BuildFile) buildFile).findRule(label.targetName().toString());
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
