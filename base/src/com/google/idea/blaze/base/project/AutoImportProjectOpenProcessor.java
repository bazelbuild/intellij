package com.google.idea.blaze.base.project;

import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectView;
import com.google.idea.blaze.base.projectview.ProjectView.Builder;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.parser.ProjectViewParser;
import com.google.idea.blaze.base.projectview.section.ListSection;
import com.google.idea.blaze.base.projectview.section.ScalarSection;
import com.google.idea.blaze.base.projectview.section.sections.AutomaticallyDeriveTargetsSection;
import com.google.idea.blaze.base.projectview.section.sections.DirectoryEntry;
import com.google.idea.blaze.base.projectview.section.sections.DirectorySection;
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
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.diagnostic.Logger;
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

  static final Key<Boolean> PROJECT_AUTO_IMPORTED = Key.create("blaze.project.auto_imported");

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
    return !Registry.is("bazel.auto.import.disabled")
            && isBazelWorkspace(virtualFile);
  }

  private boolean isBazelWorkspace(VirtualFile virtualFile) {
    return virtualFile.findChild("WORKSPACE") != null
        || virtualFile.findChild("WORKSPACE.bazel") != null;
  }

  @Override
  public @Nullable
  Project doOpenProject(
      @NotNull VirtualFile virtualFile,
      @Nullable Project projectToClose,
      boolean forceOpenInNewFrame
  ) {

    ProjectManager pm = ProjectManager.getInstance();
    if (projectToClose != null) {
      pm.closeAndDispose(projectToClose);
    }

    Project newProject = createProject(virtualFile);
    Objects.requireNonNull(newProject);

    newProject.putUserData(PROJECT_AUTO_IMPORTED, true);

    Path projectFilePath = Paths.get(Objects.requireNonNull(newProject.getProjectFilePath()));
    ProjectUtil.updateLastProjectLocation(projectFilePath);

    ProjectManagerEx.getInstanceEx()
            .openProject(
                    projectFilePath,
                    BaseSdkCompat.createOpenProjectTask(newProject)
            );
    SaveAndSyncHandler.getInstance().scheduleProjectSave(newProject);
    return newProject;
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

    projectViewBuilder.add(
        ListSection
            .builder(DirectorySection.KEY)
            .add(DirectoryEntry.include(new WorkspacePath(".")))
            .build()
    );

    projectViewBuilder.add(TextBlockSection.of(TextBlock.newLine()));

    projectViewBuilder.add(
        ScalarSection
            .builder(AutomaticallyDeriveTargetsSection.KEY)
            .set(false)
            .build()
    );

    return projectViewBuilder.build();
  }

  private ProjectView createProjectView(VirtualFile workspaceRoot,
      WorkspacePathResolver pathResolver) {

    // first check env for project view template
    String projectViewFileFromEnv = System.getenv(
        "INTELLIJ_BAZEL_PROJECT_VIEW_TEMPLATE");

    if (projectViewFileFromEnv != null) {
      return fromFileProjectView(Paths.get(projectViewFileFromEnv), pathResolver);
    }

    // second check managed project view template
    Path managedProjectViewFilePath = workspaceRoot.toNioPath()
        .resolve("tools/intellij/.managed.bazelproject");
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
