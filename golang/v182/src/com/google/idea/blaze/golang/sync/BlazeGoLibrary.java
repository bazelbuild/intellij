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
package com.google.idea.blaze.golang.sync;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.io.FileOperationProvider;
import com.google.idea.blaze.base.io.VfsUtils;
import com.google.idea.blaze.base.model.BlazeLibrary;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.LibraryKey;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.sync.data.BlazeDataStorage;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.projectview.ImportRoots;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library.ModifiableModel;
import com.intellij.openapi.util.io.FileUtilRt;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/** Add all out-of-project go sources to a library via symlinks. */
@Immutable
public class BlazeGoLibrary extends BlazeLibrary {
  private static final Logger logger = Logger.getInstance(BlazeGoLibrary.class);
  public static BoolExperiment useGoLibrary = new BoolExperiment("use.go.library", true);

  private static final long serialVersionUID = 1L;

  /** Map of import path to source files. */
  private final ImmutableMultimap<String, File> files;

  BlazeGoLibrary(BlazeProjectData projectData) {
    super(new LibraryKey("blaze_go_library"));
    ImmutableMultimap.Builder<String, File> builder = ImmutableMultimap.builder();
    for (TargetIdeInfo target : projectData.targetMap.targets()) {
      if (target.goIdeInfo == null || target.goIdeInfo.importPath == null) {
        continue;
      }
      builder.putAll(target.goIdeInfo.importPath, getSourceFiles(target, projectData));
    }
    files = builder.build();
  }

  @Override
  public void modifyLibraryModel(
      Project project,
      ArtifactLocationDecoder artifactLocationDecoder,
      ModifiableModel libraryModel) {
    File goLibrary = resetLibraryRoot(project);
    if (goLibrary == null) {
      return;
    }
    for (String importPath : files.keySet()) {
      createSymlinks(project, goLibrary, importPath, files.get(importPath));
    }
    Optional.of(goLibrary)
        .map(VfsUtils::resolveVirtualFile)
        .ifPresent(f -> libraryModel.addRoot(f, OrderRootType.CLASSES));
  }

  /**
   * Create symlinks for out-of-project source files.
   *
   * <p>Return the same collection of sources as given, but with out-of-project files replaced by
   * their symlinks.
   */
  public static Collection<File> createSymlinks(
      Project project, String importPath, Collection<File> sources) {
    File goLibrary = getLibraryRoot(project);
    if (goLibrary == null) {
      return sources;
    }
    return createSymlinks(project, goLibrary, importPath, sources);
  }

  private static Collection<File> createSymlinks(
      Project project, File goLibrary, String importPath, Collection<File> sources) {
    Map<File, File> symlinkMap = getSymlinkMap(project, importPath, sources);
    Map<File, File> symlinksOnly =
        symlinkMap
            .entrySet()
            .stream()
            .filter(e -> e.getKey() != e.getValue())
            .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
    if (symlinksOnly.isEmpty()) {
      return sources;
    }
    FileOperationProvider fileOperations = FileOperationProvider.getInstance();
    File goPackageDirectory = getGoPackageDirectory(goLibrary, importPath);
    if (!fileOperations.mkdirs(goPackageDirectory)) {
      return sources;
    }
    for (Entry<File, File> entry : symlinksOnly.entrySet()) {
      File file = entry.getKey();
      File link = entry.getValue();
      try {
        fileOperations.createSymbolicLink(link, file);
      } catch (IOException e) {
        logger.warn(e);
      }
    }
    return symlinkMap.values();
  }

  public static Map<File, File> getSymlinkMap(Project project, TargetIdeInfo target) {
    if (target.goIdeInfo == null || target.goIdeInfo.importPath == null) {
      return ImmutableMap.of();
    }
    BlazeProjectData projectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (projectData == null) {
      return ImmutableMap.of();
    }
    return getSymlinkMap(project, target.goIdeInfo.importPath, getSourceFiles(target, projectData));
  }

  private static Map<File, File> getSymlinkMap(
      Project project, String importPath, Collection<File> files) {
    BlazeProjectData projectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (projectData == null) {
      return ImmutableMap.of();
    }
    Map<File, File> symlinkMap =
        files.stream().distinct().collect(Collectors.toMap(f -> f, f -> f));
    WorkspaceRoot workspaceRoot = WorkspaceRoot.fromProjectSafe(project);
    ImportRoots importRoots = ImportRoots.forProjectSafe(project);
    if (workspaceRoot == null || importRoots == null) {
      return symlinkMap;
    }
    File goLibrary = getLibraryRoot(project);
    if (goLibrary == null) {
      return symlinkMap;
    }
    File goPackageDirectory = getGoPackageDirectory(goLibrary, importPath);
    for (File file : files) {
      WorkspacePath path = workspaceRoot.workspacePathForSafe(file);
      if (path == null || !importRoots.containsWorkspacePath(path)) {
        File link = new File(goPackageDirectory, hashName(file));
        symlinkMap.put(file, link);
      }
    }
    return symlinkMap;
  }

  private static File getGoPackageDirectory(File goLibrary, String importPath) {
    return new File(goLibrary, importPath.replace('/', '-'));
  }

  /** Source files can come from multiple directories. Hash name to prevent collision. */
  private static String hashName(File src) {
    String name = src.getName();
    return FileUtilRt.getNameWithoutExtension(name)
        + "_"
        + Integer.toHexString(src.getParent().hashCode())
        + "."
        + FileUtilRt.getExtension(name);
  }

  private static Collection<File> getSourceFiles(
      TargetIdeInfo target, BlazeProjectData projectData) {
    if (target.kind == Kind.GO_WRAP_CC) {
      return ImmutableList.of(getWrapCcGoFile(target, projectData.blazeInfo));
    }
    return Preconditions.checkNotNull(target.goIdeInfo)
        .sources
        .stream()
        .map(projectData.artifactLocationDecoder::decode)
        .collect(Collectors.toList());
  }

  private static File getWrapCcGoFile(TargetIdeInfo target, BlazeInfo blazeInfo) {
    String blazePackage = target.key.label.blazePackage().relativePath();
    File directory = new File(blazeInfo.getGenfilesDirectory(), blazePackage);
    String filename = blazePackage + '/' + target.key.label.targetName() + ".go";
    filename = filename.replace("_", "__");
    filename = filename.replace('/', '_');
    return new File(directory, filename);
  }

  @Nullable
  private static File resetLibraryRoot(Project project) {
    File goLibrary = getLibraryRoot(project);
    if (goLibrary == null) {
      return null;
    }
    FileOperationProvider fileOperations = FileOperationProvider.getInstance();
    if (fileOperations.exists(goLibrary)) {
      try {
        fileOperations.deleteRecursively(goLibrary);
      } catch (IOException e) {
        logger.warn(e);
        return null;
      }
    }
    return fileOperations.mkdirs(goLibrary) ? goLibrary : null;
  }

  @Nullable
  public static File getLibraryRoot(Project project) {
    if (!useGoLibrary.getValue()) {
      return null;
    }
    BlazeImportSettings importSettings =
        BlazeImportSettingsManager.getInstance(project).getImportSettings();
    if (importSettings == null) {
      return null;
    }
    File libraries = new File(BlazeDataStorage.getProjectDataDir(importSettings), "libraries");
    return new File(libraries, "go");
  }
}
