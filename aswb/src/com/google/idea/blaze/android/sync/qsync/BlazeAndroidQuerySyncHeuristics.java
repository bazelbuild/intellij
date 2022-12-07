/*
 * Copyright 2022 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.sync.qsync;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Queues;
import com.google.common.collect.Sets;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.qsync.BuildGraph;
import com.google.idea.blaze.base.qsync.ProjectUpdater;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.sync.projectview.ImportRoots;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;

/**
 * Heuristic utilities for android support in the new query-sync. This class will be replaced or
 * largely augmented by a more robust version of {@link BuildGraph} *
 */
public class BlazeAndroidQuerySyncHeuristics {
  private BlazeAndroidQuerySyncHeuristics() {}

  private static final Logger logger = Logger.getInstance(BlazeAndroidQuerySyncHeuristics.class);

  /**
   * Walks the directory structure for directories that: 1. Contain an "AndroidManifest.xml" file 2.
   * Contain a directory named "res" and returns a list of all such "res" directories.
   *
   * <p>A heuristic for finding Android resource directories to be replaced when {@link BuildGraph}
   * API is stable.
   */
  public static ImmutableList<File> collectAndroidResourceDirectories(
      Project project, WorkspaceRoot workspaceRoot) {
    List<File> resourceDirectories = Lists.newArrayList();
    BlazeImportSettings importSettings =
        BlazeImportSettingsManager.getInstance(project).getImportSettings();
    ProjectViewSet projectViewSet = ProjectViewManager.getInstance(project).getProjectViewSet();
    ImportRoots ir =
        ImportRoots.builder(workspaceRoot, importSettings.getBuildSystem())
            .add(projectViewSet)
            .build();

    Queue<File> toVisit = Queues.newArrayDeque();
    ir.rootDirectories().stream().map(workspaceRoot::fileForPath).forEach(toVisit::add);
    while (!toVisit.isEmpty()) {
      File current = toVisit.poll();
      if (current.isDirectory()) {
        File maybeManifest = new File(current, "AndroidManifest.xml");
        File maybeResDirectory = new File(current, "res");
        if (maybeManifest.exists() && maybeResDirectory.isDirectory()) {
          resourceDirectories.add(maybeResDirectory);
        }

        for (File child : Objects.requireNonNull(current.listFiles())) {
          if (child.isDirectory()) {
            toVisit.add(child);
          }
        }
      }
    }

    return ImmutableList.copyOf(resourceDirectories);
  }

  /**
   * Queries {@link BuildGraph} for source-files and returns a set of all java packages. Assumes at
   * most one package per directory.
   *
   * <p>Heuristic for finding source directories to be replaced when {@link BuildGraph} API is
   * stable.
   */
  public static ImmutableSet<String> collectSourcePackages(BuildGraph buildGraph) {

    Set<String> sourcePackages = Sets.newHashSet();
    Set<String> visitedDirs = Sets.newHashSet();

    for (String sourceFile : buildGraph.getSourceFiles()) {
      Path sourcePath = Paths.get(sourceFile);
      Path parentDir = sourcePath.getParent();
      if (visitedDirs.contains(parentDir.toString())) {
        continue;
      }
      try {
        String sourcePackage = ProjectUpdater.readPackage(sourceFile);
        if (!sourcePackage.isEmpty()) {
          sourcePackages.add(sourcePackage);
          visitedDirs.add(parentDir.toString());
        }

      } catch (IOException ioe) {
        logger.warn(String.format("Exception reading package for %s", sourceFile), ioe);
      }
    }
    return ImmutableSet.copyOf(sourcePackages);
  }
}
