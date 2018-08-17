/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.golang.resolve;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.io.VfsUtils;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.prefetch.FetchExecutor;
import com.google.idea.blaze.base.prefetch.PrefetchIndexingTask;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.VetoableProjectManagerListener;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

class BlazeGoSymlinkPreresolver implements ApplicationComponent {
  private static final Logger logger = Logger.getInstance(BlazeGoSymlinkPreresolver.class);

  private static final BoolExperiment preresolveGoSymlinksOnProjectOpen =
      new BoolExperiment("preresolve.go.symlinks.on.project.open", true);

  @Override
  public void initComponent() {
    ProjectManager.getInstance()
        .addProjectManagerListener(
            new VetoableProjectManagerListener() {
              @Override
              public boolean canClose(Project project) {
                return true;
              }

              @Override
              public void projectOpened(Project project) {
                if (preresolveGoSymlinksOnProjectOpen.getValue()) {
                  preresolveGoSymlinks(project);
                }
              }
            });
  }

  private static void preresolveGoSymlinks(Project project) {
    if (!Blaze.isBlazeProject(project)) {
      return;
    }
    BlazeProjectData projectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (projectData == null
        || !projectData.workspaceLanguageSettings.isLanguageActive(LanguageClass.GO)) {
      return;
    }
    File goRoot = BlazeGoRootsProvider.getGoRoot(project);
    if (goRoot == null || !goRoot.exists()) {
      return;
    }
    PrefetchIndexingTask.submitPrefetchingTask(
        project, resolveGoSymlinks(goRoot), "Prefetching Go files");
  }

  private static ListenableFuture<List<VirtualFile>> resolveGoSymlinks(File goRoot) {
    List<ListenableFuture<VirtualFile>> futures = new ArrayList<>();
    try {
      Files.walkFileTree(
          goRoot.toPath(),
          new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
              futures.add(
                  FetchExecutor.EXECUTOR.submit(
                      () -> {
                        VirtualFile virtualFile = VfsUtils.resolveVirtualFile(file.toFile());
                        return virtualFile != null ? virtualFile.getCanonicalFile() : null;
                      }));
              return FileVisitResult.CONTINUE;
            }
          });
    } catch (IOException e) {
      logger.warn(e);
    }
    return Futures.allAsList(futures);
  }
}
