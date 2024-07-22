package com.google.idea.blaze.clwb.base;

import static com.google.common.truth.Truth.assertThat;

import com.google.idea.blaze.base.async.process.ExternalTask;
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
import com.google.idea.blaze.base.sync.aspects.strategy.AspectRepositoryProvider;
import com.google.idea.blaze.base.sync.data.BlazeDataStorage;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolverImpl;
import com.google.idea.blaze.base.toolwindow.TasksToolWindowFactory;
import com.google.idea.blaze.base.wizard2.BlazeProjectCommitException;
import com.google.idea.blaze.base.wizard2.BlazeProjectImportBuilder;
import com.google.idea.blaze.base.wizard2.CreateFromScratchProjectViewOption;
import com.google.idea.blaze.base.wizard2.WorkspaceTypeData;
import com.google.idea.testing.ServiceHelper;
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
import com.google.idea.blaze.base.sync.SyncPhaseCoordinator;
import com.jetbrains.cidr.lang.CLanguageKind;
import com.jetbrains.cidr.lang.OCLanguageUtils;
import com.jetbrains.cidr.lang.psi.OCFile;
import com.jetbrains.cidr.lang.workspace.OCCompilerSettings;
import com.jetbrains.cidr.lang.workspace.OCResolveConfiguration;
import com.jetbrains.cidr.lang.workspace.OCWorkspace;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import org.intellij.lang.annotations.Language;

public abstract class ClwbIntegrationTestCase extends HeavyPlatformTestCase {
  protected VirtualFile myProjectRoot;

  /**
   * Gets the path to the test project and performs some basic checks. The path
   * is provided by `bazel_integration_test` rule in the `BIT_WORKSPACE_DIR`
   * environment variable.
   */
  private static File getTestProjectRoot() {
    final var bitWorkspaceDir = System.getenv("BIT_WORKSPACE_DIR");
    assertNotNull(bitWorkspaceDir);

    final var file = new File(bitWorkspaceDir);
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
    assertNotNull(bitBazelBinary);

    final var errStream = new ByteArrayOutputStream();

    // run bazel binary in project root to avoid downloading it twice
    final var result = ExternalTask.builder(getTestProjectRoot())
        .args(bitBazelBinary, "--version")
        .stderr(errStream)
        .build()
        .runAsync()
        .get();

    if (result != 0) {
      fail("cannot run bazel binary: " + errStream);
    }

    return bitBazelBinary;
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    final var bazelBinary = getTestBazelPath();
    BlazeUserSettings.getInstance().setBazelBinaryPath(bazelBinary);

    // register the tasks toolwindow, needs to be done manually
    final var windowManager = (ToolWindowHeadlessManagerImpl) ToolWindowManager.getInstance(myProject);
    windowManager.doRegisterToolWindow(TasksToolWindowFactory.ID);

    // override aspect repository, because they are in a different place during testing
    ServiceHelper.registerExtensionFirst(
        AspectRepositoryProvider.EP_NAME,
        new TestAspectRepositoryProvider(),
        getTestRootDisposable()
    );

    // create the temp directory, because CLion expected it to be present but bazel does not create it
    final var tmpDir = new File(FileUtil.getTempDirectory());
    tmpDir.mkdirs();
  }

  /**
   * Very similar to {@link AutoImportProjectOpenProcessor}.
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

    final var projectViewLines = projectViewText().split("\n");
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

    builder.builder().commitToProject(myProject); // required to trigger sync
  }

  protected @Language("projectview") String projectViewText() {
    return """
directories:
  .

derive_targets_from_directories: true
    """;
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

  protected OCFile findProjectOCFile(String relativePath) {
    final var psiFile = findProjectPsiFile(relativePath);
    assertThat(psiFile).isInstanceOf(OCFile.class);

    return (OCFile) psiFile;
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
