package com.google.idea.blaze.base;

import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.ExternalWorkspace;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.TargetName;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.intellij.psi.PsiFile;

import java.io.File;
import java.nio.file.Paths;

import static com.google.common.truth.Truth.assertThat;
import static com.google.idea.blaze.base.settings.ui.ProjectViewUi.getProject;
import static org.junit.Assert.assertNotNull;

public class ExternalWorkspaceFixture {
  public final ExternalWorkspace workspace;

  final TestFileSystem fileSystem;
  WorkspaceFileSystem workspaceFileSystem;

  public ExternalWorkspaceFixture(ExternalWorkspace workspace, TestFileSystem fileSystem) {
    this.workspace = workspace;
    this.fileSystem = fileSystem;
  }

  public BuildFile createBuildFile(WorkspacePath workspacePath, String... contentLines) {
    PsiFile file = getWorkspaceFileSystem().createPsiFile(workspacePath, contentLines);
    assertThat(file).isInstanceOf(BuildFile.class);
    return (BuildFile) file;
  }

  WorkspaceFileSystem getWorkspaceFileSystem() {
    if (workspaceFileSystem == null) {
      BlazeProjectData blazeProjectData = BlazeProjectDataManager.getInstance(getProject()).getBlazeProjectData();
      assertNotNull(blazeProjectData);

      File outputBase = blazeProjectData.getBlazeInfo().getOutputBase();


      WorkspaceRoot workspaceRoot = new WorkspaceRoot(Paths.get(
          blazeProjectData.getBlazeInfo().getOutputBase().getAbsolutePath(),
          "external", workspace.name()).normalize().toFile());

      File workspaceRootFile = workspaceRoot.directory();
      assertThat(workspaceRootFile).isNotNull();
      workspaceFileSystem = new WorkspaceFileSystem(workspaceRoot, fileSystem);
    }

    return workspaceFileSystem;
  }

  public Label createLabel(WorkspacePath packagePath, TargetName targetName) {
    return Label.create(workspace.repoName(), packagePath, targetName);
  }
}
