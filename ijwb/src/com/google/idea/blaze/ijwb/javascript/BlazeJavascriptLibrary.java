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
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.io.FileOperationProvider;
import com.google.idea.blaze.base.io.VfsUtils;
import com.google.idea.blaze.base.model.BlazeLibrary;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.LibraryKey;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.sync.data.BlazeDataStorage;
import com.google.idea.blaze.base.sync.projectview.ImportRoots;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.ijwb.typescript.TypescriptPrefetchFileSource;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library.ModifiableModel;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.File;
import java.io.IOException;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * Contains all out-of-project-view source files in the transitive closure, for resolving symbols.
 *
 * <p>Adding each source file directly would create too many watch roots, so we symlink every js/ts
 * source file and add them as one library.
 *
 * <p>Also includes typescript sources, so we don't need two massive trees of symlinks.
 */
@Immutable
public class BlazeJavascriptLibrary extends BlazeLibrary {
  private static final Logger logger = Logger.getInstance(BlazeJavascriptLibrary.class);
  static BoolExperiment useJavascriptLibrary = new BoolExperiment("use.javascript.library2", true);

  private static final long serialVersionUID = 1L;

  private final ImmutableList<ArtifactLocation> librarySources;

  BlazeJavascriptLibrary(BlazeProjectData projectData) {
    super(new LibraryKey("blaze_javascript_library"));
    Set<String> libraryExtensions =
        Sets.union(
            JavascriptPrefetchFileSource.getJavascriptExtensions(),
            TypescriptPrefetchFileSource.getTypescriptExtensions());
    Predicate<ArtifactLocation> hasLibraryExtensions =
        (location) -> {
          String extension = Files.getFileExtension(location.getRelativePath());
          return libraryExtensions.contains(extension);
        };
    librarySources =
        projectData
            .targetMap
            .targets()
            .stream()
            .flatMap(
                target ->
                    Stream.concat(
                        target.jsIdeInfo != null
                            ? target.jsIdeInfo.sources.stream()
                            : Stream.empty(),
                        target.tsIdeInfo != null
                            ? target.tsIdeInfo.sources.stream()
                            : Stream.empty()))
            .filter(hasLibraryExtensions)
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
    Predicate<ArtifactLocation> isLibrarySourceOutsideProject =
        (location) -> {
          if (!location.isSource) {
            return true;
          }
          WorkspacePath workspacePath = WorkspacePath.createIfValid(location.getRelativePath());
          return workspacePath == null || !importRoots.containsWorkspacePath(workspacePath);
        };
    File libraryRoot = resetLibraryRoot(project);
    if (libraryRoot == null) {
      return;
    }
    librarySources
        .stream()
        .filter(isLibrarySourceOutsideProject)
        .forEach(f -> createSymlink(f, artifactLocationDecoder, libraryRoot));
    VirtualFile rootVirtualFile = VfsUtils.resolveVirtualFile(libraryRoot);
    if (rootVirtualFile == null) {
      return;
    }
    libraryModel.addRoot(rootVirtualFile, OrderRootType.CLASSES);
  }

  @Nullable
  private static File resetLibraryRoot(Project project) {
    BlazeImportSettings importSettings =
        BlazeImportSettingsManager.getInstance(project).getImportSettings();
    if (importSettings == null) {
      return null;
    }
    File libraries = new File(BlazeDataStorage.getProjectDataDir(importSettings), "libraries");
    File javascriptLibrary = new File(libraries, "javascript");
    FileOperationProvider fileOperations = FileOperationProvider.getInstance();
    if (fileOperations.exists(javascriptLibrary)) {
      try {
        fileOperations.deleteRecursively(javascriptLibrary);
      } catch (IOException e) {
        logger.error(e);
        return null;
      }
    }
    return javascriptLibrary;
  }

  private static synchronized void createSymlink(
      ArtifactLocation location, ArtifactLocationDecoder decoder, File root) {
    File target = decoder.decode(location);
    File link = new File(root, location.getRelativePath());
    File directory = link.getParentFile();
    FileOperationProvider fileOperations = FileOperationProvider.getInstance();
    if (!fileOperations.exists(directory)) {
      if (!fileOperations.mkdirs(directory)) {
        return;
      }
    }
    if (!fileOperations.exists(link)) {
      try {
        fileOperations.createSymbolicLink(link, target);
      } catch (IOException e) {
        logger.warn(e);
      }
    }
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
