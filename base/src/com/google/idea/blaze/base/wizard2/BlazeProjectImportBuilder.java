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
package com.google.idea.blaze.base.wizard2;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.intellij.ide.util.projectWizard.ProjectBuilder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.util.io.FileUtil;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** Wrapper around a {@link BlazeNewProjectBuilder} to fit into IntelliJ's import framework. */
@VisibleForTesting
public class BlazeProjectImportBuilder extends ProjectBuilder {
  private static final Logger LOG = Logger.getInstance(BlazeProjectImportBuilder.class);
  private BlazeNewProjectBuilder builder = new BlazeNewProjectBuilder();

  @Nullable
  @Override
  public List<Module> commit(
      Project project,
      @Nullable ModifiableModuleModel modifiableModuleModel,
      ModulesProvider modulesProvider) {
    builder.commitToProject(project);
    return ImmutableList.of();
  }

  public BlazeNewProjectBuilder builder() {
    return builder;
  }

  @Override
  public @Nullable Project createProject(String name, String path) {
    Path currentPath = Path.of(path);
    Path workspacePath = currentPath.getParent();
    Path workspaceIdeaPath = workspacePath.resolve(Project.DIRECTORY_STORE_FOLDER);
    File workspaceIdeaDir = workspaceIdeaPath.toFile();
    // Allows checked in files to be used either using .idea under workspace root or checked in.
    // createProject would delete the directory if it exists, so using loadProject instead.
    if (workspaceIdeaDir.exists() && workspaceIdeaDir.isDirectory()) {
      Path bazelProjectPath = currentPath.resolve(Project.DIRECTORY_STORE_FOLDER);
      try {
        FileUtil.copyDirContent(workspaceIdeaDir, bazelProjectPath.toFile());
        return ProjectManagerEx.getInstanceEx().loadProject(currentPath);
      } catch (IOException e) {
        LOG.error("Failed copying content of workspace .idea directory to bazel project", e);
        try {
          FileUtil.deleteRecursively(bazelProjectPath);
          FileUtil.ensureExists(bazelProjectPath.toFile());
        } catch (IOException ex) {
          //I tried...
          throw new RuntimeException("Project creation failed and was unrecoverable", ex);
        }
      }
    }

    AtomicReference<Project> newProjectRef = new AtomicReference<>();
    ApplicationManager.getApplication().invokeAndWait(() -> {
      newProjectRef.set(super.createProject(name, path));
    });
    return newProjectRef.get();
  }
}
