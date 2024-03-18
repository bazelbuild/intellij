/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.qsync.artifacts;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.idea.blaze.base.qsync.FileRefresher;
import com.google.idea.blaze.common.Context;
import com.google.idea.blaze.common.artifact.BuildArtifactCache;
import com.google.idea.blaze.exception.BuildException;
import com.google.idea.blaze.qsync.BlazeProjectSnapshot;
import com.google.idea.blaze.qsync.artifacts.ArtifactDirectoryUpdate;
import com.google.idea.blaze.qsync.project.ProjectProto.ArtifactDirectories;
import com.google.idea.blaze.qsync.project.ProjectProto.ArtifactDirectoryContents;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Maintains a set of directories inside the IDE project dir, with the contents of each determined
 * entirely by the proto proto.
 *
 * <p>For each directory given inside {@link ArtifactDirectories}, this class ensures that it's
 * contents exactly match the proto, including deleting any entries not present in the proto. Any
 * other directories inside the IDE project dir are ignored.
 */
public class ProjectArtifactStore {

  private final Path projectDir;
  private final Path workspacePath;
  private final BuildArtifactCache artifactCache;
  private final FileRefresher fileRefresher;

  public ProjectArtifactStore(
      Path projectDir,
      Path workspacePath,
      BuildArtifactCache artifactCache,
      FileRefresher fileRefresher) {
    this.projectDir = projectDir;
    this.workspacePath = workspacePath;
    this.artifactCache = artifactCache;
    this.fileRefresher = fileRefresher;
  }

  public void update(Context<?> context, BlazeProjectSnapshot graph) throws BuildException {
    List<IOException> exceptions = Lists.newArrayList();
    ImmutableSet.Builder<Path> updatedPaths = ImmutableSet.builder();
    for (Map.Entry<String, ArtifactDirectoryContents> entry :
        graph.project().getArtifactDirectories().getDirectoriesMap().entrySet()) {
      Path root = projectDir.resolve(entry.getKey());
      ArtifactDirectoryUpdate dirUpdate =
          new ArtifactDirectoryUpdate(artifactCache, workspacePath, root, entry.getValue());
      try {
        dirUpdate.update();
      } catch (IOException e) {
        exceptions.add(e);
      }
      updatedPaths.addAll(dirUpdate.getUpdatedPaths());
    }
    fileRefresher.refreshFiles(context, updatedPaths.build());
    if (!exceptions.isEmpty()) {
      BuildException e = new BuildException("Artifact store update failed.");
      exceptions.stream().forEach(e::addSuppressed);
      throw e;
    }
  }
}
