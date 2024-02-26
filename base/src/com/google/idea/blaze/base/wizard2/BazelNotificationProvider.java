package com.google.idea.blaze.base.wizard2;

import com.google.idea.blaze.base.bazel.BazelWorkspaceRootProvider;
import com.google.idea.blaze.base.lang.buildfile.language.BuildFileType;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings.ProjectType;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolverImpl;
import com.intellij.ide.DataManager;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotificationPanel.Status;
import com.intellij.ui.EditorNotificationProvider;
import java.io.File;
import java.util.function.Function;
import javax.swing.JComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BazelNotificationProvider implements EditorNotificationProvider, DumbAware {

  private static class ImportAction extends AnAction {

    final File workspaceRootFile;

    ImportAction(File workspaceRootFile) {
      this.workspaceRootFile = workspaceRootFile;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent anActionEvent) {
      BlazeNewProjectWizard wizard = new BlazeNewProjectWizard() {
        protected void init() {
          BazelWorkspaceRootProvider.INSTANCE.isWorkspaceRoot(workspaceRootFile);
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

          super.init();
        }

        @Override
        protected ProjectImportWizardStep[] getSteps(WizardContext context) {
          return new ProjectImportWizardStep[]{
              new BlazeSelectProjectViewImportWizardStep(context),
              new BlazeEditProjectViewImportWizardStep(context)
          };
        }
      };

      if (!wizard.showAndGet()) {
        return;
      }
      BlazeProjectCreator projectCreator = new BlazeProjectCreator(wizard.builder);
      BlazeImportProjectAction.createFromWizard(projectCreator, wizard.context);
    }
  }

  @Override
  public @Nullable Function<? super FileEditor, ? extends JComponent> collectNotificationData(
      @NotNull Project project, @NotNull VirtualFile file) {
    if (file.getFileType() != BuildFileType.INSTANCE) {
      return null;
    }
    if (Blaze.getProjectType(project) != ProjectType.UNKNOWN) {
      return null;
    }

    String root = project.getBasePath();
    if (root == null) {
      return null;
    }

    File rootFile = new File(root);
    if (!BazelWorkspaceRootProvider.INSTANCE.isWorkspaceRoot(new File(root))) {
      return null;
    }

    ImportAction action = new ImportAction(rootFile);

    return fileEditor -> {
      EditorNotificationPanel panel = new EditorNotificationPanel(fileEditor, Status.Warning);
      panel.setText("Project is not configured");
      panel.createActionLabel("Import Bazel project", () -> {
        DataContext dataContext = DataManager.getInstance().getDataContext(panel);
        AnActionEvent event = AnActionEvent.createFromAnAction(action, null, ActionPlaces.UNKNOWN, dataContext);
        if (ActionUtil.lastUpdateAndCheckDumb(action, event, true)) {
          ActionUtil.performActionDumbAwareWithCallbacks(action, event);
        }
      });

      return panel;
    };
  }
}
