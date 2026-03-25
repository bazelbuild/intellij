/*
 * Copyright 2026 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.idea.blaze.base;

import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.ExternalWorkspace;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.TargetName;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;

import java.io.File;
import java.nio.file.Paths;

import static com.google.common.truth.Truth.assertThat;
import static com.google.idea.blaze.base.settings.ui.ProjectViewUi.getProject;
import static org.junit.Assert.assertNotNull;

public class ExternalWorkspaceFixture {
  public final ExternalWorkspace workspace;

  final TestFileSystem fileSystem;
  final CodeInsightTestFixture testFixture;
  WorkspaceFileSystem workspaceFileSystem;

  public ExternalWorkspaceFixture(ExternalWorkspace workspace, TestFileSystem fileSystem, CodeInsightTestFixture testFixture) {
    this.workspace = workspace;
    this.fileSystem = fileSystem;
    this.testFixture = testFixture;
  }

  public VirtualFile createDirectory(WorkspacePath path) {
    return getWorkspaceFileSystem().createDirectory(path);
  }

  public BuildFile createBuildFile(WorkspacePath workspacePath, String... contentLines) {
    PsiFile file = getWorkspaceFileSystem().createPsiFile(workspacePath, contentLines);
    assertThat(file).isInstanceOf(BuildFile.class);
    return (BuildFile) file;
  }

  protected BuildFile configureByFile(WorkspacePath workspacePath, String... contentLines) {
    PsiFile file = getWorkspaceFileSystem().createPsiFile(workspacePath, contentLines);
    assertThat(file).isInstanceOf(BuildFile.class);
    testFixture.configureFromExistingVirtualFile(file.getVirtualFile());
    return (BuildFile) file;
  }


  WorkspaceFileSystem getWorkspaceFileSystem() {
    if (workspaceFileSystem == null) {
      BlazeProjectData blazeProjectData = BlazeProjectDataManager.getInstance(getProject()).getBlazeProjectData();
      assertNotNull(blazeProjectData);

      File outputBase = blazeProjectData.blazeInfo().getOutputBase();


      WorkspaceRoot workspaceRoot = new WorkspaceRoot(Paths.get(
          blazeProjectData.blazeInfo().getOutputBase().getAbsolutePath(),
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
