package com.google.idea.blaze.base.project;

import static com.google.idea.blaze.base.project.BlazeProjectOpenProcessor.getIdeaSubdirectory;

import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectView;
import com.google.idea.blaze.base.projectview.ProjectView.Builder;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.parser.ProjectViewParser;
import com.google.idea.blaze.base.projectview.section.sections.TextBlock;
import com.google.idea.blaze.base.projectview.section.sections.TextBlockSection;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.google.idea.blaze.base.sync.data.BlazeDataStorage;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolverImpl;
import com.google.idea.blaze.base.wizard2.BlazeProjectCommitException;
import com.google.idea.blaze.base.wizard2.BlazeProjectImportBuilder;
import com.google.idea.blaze.base.wizard2.CreateFromScratchProjectViewOption;
import com.google.idea.blaze.base.wizard2.WorkspaceTypeData;
import com.google.idea.sdkcompat.general.BaseSdkCompat;
import com.intellij.ide.SaveAndSyncHandler;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectImport.ProjectOpenProcessor;
import icons.BlazeIcons;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import javax.swing.Icon;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Adds {@link ProjectOpenProcessor} to import project by opening it directly from a directory
 * <p>
 * Project view can be created from (in order of existence):
 * <ul>
 *  <li>ENV var: INTELLIJ_BAZEL_PROJECT_VIEW_TEMPLATE=/home/user/some.bazelproject</li>
 *  <li>workspace - tools/intellij/.managed.bazelproject</li>
 *  <li>if above do not exist, creates minimal project view file</li>
 * </ul>
 * <p>
 * Must be loaded after {@link BlazeProjectOpenProcessor} to only import new projects.
 */
public class AutoImportProjectOpenProcessor extends ProjectOpenProcessor {

  public static final String MANAGED_PROJECT_RELATIVE_PATH = "tools/intellij/.managed.bazelproject";
  public static final String PROJECT_VIEW_FROM_ENV = "INTELLIJ_BAZEL_PROJECT_VIEW_TEMPLATE";

  static final Key<Boolean> PROJECT_AUTO_IMPORTED = Key.create("bazel.project.auto_imported");

  private static final Logger LOG = Logger.getInstance(AutoImportProjectOpenProcessor.class);

  @Override
  public @NotNull
  @Nls
  String getName() {
    return Blaze.defaultBuildSystemName() + " Project";
  }

  @javax.annotation.Nullable
  @Override
  public Icon getIcon() {
    return BlazeIcons.Logo;
  }

  @Override
  public boolean isStrongProjectInfoHolder() {
    return true;
  }

  @Override
  public boolean lookForProjectsInDirectory() {
    return false;
  }

  @Override
  public boolean canOpenProject(@NotNull VirtualFile virtualFile) {
    // Auto import activated only if it is not disabled, there is no existing project model in the folder
    // and Bazel workspace is detected.
    return !Registry.is("bazel.auto.import.disabled")
            && getIdeaSubdirectory(virtualFile) == null && isBazelWorkspace(virtualFile);
  }

  private boolean isBazelWorkspace(VirtualFile virtualFile) {
    return virtualFile.findChild("WORKSPACE") != null
        || virtualFile.findChild("WORKSPACE.bazel") != null
        || virtualFile.findChild("MODULE.bazel") != null;
  }

  @Override
  public @Nullable
  Project doOpenProject(
      @NotNull VirtualFile virtualFile,
      @Nullable Project projectToClose,
      boolean forceOpenInNewFrame
  ) {
    try {
      return ProgressManager.getInstance().run(new Task.WithResult<Project, Exception>(null, "Importing Project...", true) {
        @Override
        protected Project compute(@NotNull ProgressIndicator progressIndicator) {
          Project newProject = createProject(virtualFile);
          Objects.requireNonNull(newProject);

          newProject.putUserData(PROJECT_AUTO_IMPORTED, true);

          Path projectFilePath = Paths.get(Objects.requireNonNull(newProject.getBasePath()));
          ProjectUtil.updateLastProjectLocation(projectFilePath);

          ProjectManagerEx.getInstanceEx()
                  .openProject(
                          projectFilePath,
                          BaseSdkCompat.createOpenProjectTask(newProject)
                                  .asNewProject()
                                  .withProjectToClose(projectToClose)
                                  .withForceOpenInNewFrame(forceOpenInNewFrame)
                  );
          SaveAndSyncHandler.getInstance().scheduleProjectSave(newProject);
          return newProject;
        }
      });
    } catch (Exception e) {
      LOG.error("Unexpected exception thrown while importing project", e);
      return null;
    }
  }

  @Nullable
  private Project createProject(@NotNull VirtualFile virtualFile) {
    String projectFilePath =
        virtualFile.getPath() + "/" + BlazeDataStorage.PROJECT_DATA_SUBDIRECTORY;

    createDirs(projectFilePath);

    String name = virtualFile.getName();

    File projectViewFile = new File(projectFilePath + "/.bazelproject");

    BlazeProjectImportBuilder builder = new BlazeProjectImportBuilder();
    File workspaceRootFile = new File(virtualFile.getPath());
    WorkspaceRoot root = new WorkspaceRoot(workspaceRootFile);

    WorkspacePathResolver pathResolver = new WorkspacePathResolverImpl(root);
    builder.builder().setWorkspaceData(
        WorkspaceTypeData.builder()
            .setWorkspaceName(workspaceRootFile.getName())
            .setWorkspaceRoot(root)
            .setCanonicalProjectDataLocation(workspaceRootFile)
            .setFileBrowserRoot(workspaceRootFile)
            .setWorkspacePathResolver(pathResolver)
            .setBuildSystem(BuildSystemName.Bazel)
            .build()
    );
    ProjectView projectView = createProjectView(virtualFile, pathResolver);

    builder.builder().setProjectView(projectView);
    builder.builder().setProjectViewFile(projectViewFile);
    builder.builder().setProjectViewSet(ProjectViewSet.builder().add(projectView).build());
    builder.builder().setProjectViewOption(new CreateFromScratchProjectViewOption());
    builder.builder().setProjectName(name);
    builder.builder().setProjectDataDirectory(projectFilePath);

    try {
      builder.builder().commit(); // set pending project settings
    } catch (BlazeProjectCommitException e) {
      LOG.error("Failed to commit project import builder", e);
    }

    Project newProject = builder.createProject(name, projectFilePath);
    if (newProject == null) {
      LOG.error("Failed to Bazel create project");
      return null;
    }

    newProject.save();

    if (!builder.validate(null, newProject)) {
      LOG.error("New Bazel project validation failed");
      return null;
    }

    builder.builder().commitToProject(newProject); // required to trigger sync
    return newProject;
  }

  private void createDirs(String projectFilePath) {
    try {
      FileUtil.ensureExists(new File(projectFilePath, Project.DIRECTORY_STORE_FOLDER));
    } catch (IOException e) {
      LOG.error("Failed creating project dirs", e);
    }
  }

  private ProjectView defaultEmptyProjectView() {
    Builder projectViewBuilder = ProjectView.builder();
    projectViewBuilder.add(TextBlockSection.of(TextBlock.of(
            "# This is a projectview file generated automatically during bazel project auto-import ",
            "# For more documentation, please visit https://ij.bazel.build/docs/project-views.html",
            "# If your repository contains predefined .projectview files, you use 'import' directive to include them.",
            "# Otherwise, please specify 'directories' and 'targets' you want to be imported",
            " ",
            "# By default, we keep the 'directories' section empty, so nothing is imported.",
            "# Please change `-.` to a list of directories you would like to import",
            "# ",
            "# After that, please look at the `derive_targets_from_directories` section and then:",
            "#   - either keep it set to `true` to import ALL targets in the directories section",
            "#   - or set it to `false` and add `targets` section to choose the targets selectively",
            "",
            "directories: ",
            "  -.",
            "derive_targets_from_directories: true",
            ""
    )));

    return projectViewBuilder.build();
  }

  private ProjectView createProjectView(VirtualFile workspaceRoot,
      WorkspacePathResolver pathResolver) {

    // first check env for project view template
    String projectViewFileFromEnv = System.getenv(PROJECT_VIEW_FROM_ENV);

    if (projectViewFileFromEnv != null) {
      return fromFileProjectView(Paths.get(projectViewFileFromEnv), pathResolver);
    }

    // second check managed project view template
    Path managedProjectViewFilePath = workspaceRoot.toNioPath()
        .resolve(MANAGED_PROJECT_RELATIVE_PATH);
    if (managedProjectViewFilePath.toFile().exists()) {
      return fromFileProjectView(managedProjectViewFilePath, pathResolver);
    }

    // create minimal project view file manually
    return defaultEmptyProjectView();
  }

  private ProjectView fromFileProjectView(
      Path projectViewFilePath,
      WorkspacePathResolver pathResolver
  ) {
    ProjectViewParser projectViewParser = new ProjectViewParser(null, pathResolver);
    projectViewParser.parseProjectView(projectViewFilePath.toFile());
    ProjectViewSet result = projectViewParser.getResult();
    return Objects.requireNonNull(result.getTopLevelProjectViewFile()).projectView;
  }
}
