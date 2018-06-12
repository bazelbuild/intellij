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
package com.google.idea.blaze.ijwb.javascript;

import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.io.VirtualFileSystemProvider;
import com.google.idea.blaze.base.model.BlazeLibrary;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.LibraryKey;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.sync.projectview.ImportRoots;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library.ModifiableModel;
import com.intellij.openapi.vfs.LocalFileSystem;
import java.util.Collection;
import java.util.Set;
import java.util.function.Predicate;
import javax.annotation.concurrent.Immutable;

/**
 * Contains all out-of-project-view source files in the transitive closure, for resolving symbols.
 */
@Immutable
public class BlazeJavascriptLibrary extends BlazeLibrary {
  private static final long serialVersionUID = 1L;

  private final ImmutableList<ArtifactLocation> librarySources;

  BlazeJavascriptLibrary(BlazeProjectData projectData) {
    super(new LibraryKey("blaze_javascript_library"));
    Set<String> javascriptExtensions = JavascriptPrefetchFileSource.getJavascriptExtensions();
    Predicate<ArtifactLocation> hasJsExtensions =
        (location) -> {
          String extension = Files.getFileExtension(location.getRelativePath());
          return javascriptExtensions.contains(extension);
        };
    librarySources =
        projectData
            .targetMap
            .targets()
            .stream()
            .filter(target -> target.jsIdeInfo != null)
            .map(target -> target.jsIdeInfo.sources)
            .flatMap(Collection::stream)
            .filter(hasJsExtensions)
            .collect(ImmutableList.toImmutableList());
  }

  @Override
  public void modifyLibraryModel(
      Project project,
      ArtifactLocationDecoder artifactLocationDecoder,
      ModifiableModel libraryModel) {
    ImportRoots importRoots = ImportRoots.forProjectSafe(project);
    if (importRoots == null) {
      return;
    }
    LocalFileSystem lfs = VirtualFileSystemProvider.getInstance().getSystem();
    Predicate<ArtifactLocation> isJavascriptSourceOutsideProject =
        (location) -> {
          if (!location.isSource) {
            return true;
          }
          WorkspacePath workspacePath = WorkspacePath.createIfValid(location.getRelativePath());
          return workspacePath == null || !importRoots.containsWorkspacePath(workspacePath);
        };
    librarySources
        .stream()
        .filter(isJavascriptSourceOutsideProject)
        .map(artifactLocationDecoder::decode)
        .map(lfs::findFileByIoFile)
        .filter(java.util.Objects::nonNull)
        .forEach(vf -> libraryModel.addRoot(vf, OrderRootType.CLASSES));
  }

  @Override
  public int hashCode() {
    return com.google.common.base.Objects.hashCode(super.hashCode(), librarySources);
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof BlazeJavascriptLibrary)) {
      return false;
    }

    BlazeJavascriptLibrary that = (BlazeJavascriptLibrary) other;

    return super.equals(other)
        && com.google.common.base.Objects.equal(librarySources, that.librarySources);
  }
}
