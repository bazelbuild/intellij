/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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

import com.goide.project.GoRootsProvider;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.io.FileOperationProvider;
import com.google.idea.blaze.base.io.VirtualFileSystemProvider;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.Scope;
import com.google.idea.blaze.base.scope.scopes.TimingScope;
import com.google.idea.blaze.base.scope.scopes.TimingScope.EventType;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.sync.SyncCache;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.golang.BlazeGoSupport;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/** Builds a tree of symlinks converting blaze project structure into go project structure. */
public class BlazeGoRootsProvider implements GoRootsProvider {
  private static final Logger logger = Logger.getInstance(BlazeGoRootsProvider.class);
  private static final String PACKAGE_TO_TARGET_KEY = "go.package.to.target";

  @Override
  public Collection<VirtualFile> getGoPathRoots(
      @Nullable Project project, @Nullable Module module) {
    return ImmutableList.of();
  }

  @Override
  public Collection<VirtualFile> getGoPathSourcesRoots(
      @Nullable Project project, @Nullable Module module) {
    if (project == null) {
      return ImmutableList.of();
    }
    BlazeProjectData projectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (projectData == null
        || !projectData.getWorkspaceLanguageSettings().isLanguageActive(LanguageClass.GO)
        || !BlazeGoSupport.blazeGoSupportEnabled.getValue()) {
      return ImmutableList.of();
    }
    File goRoot = getGoRoot(project);
    if (goRoot == null) {
      return ImmutableList.of();
    }
    VirtualFile goRootVF =
        VirtualFileSystemProvider.getInstance().getSystem().findFileByIoFile(goRoot);
    return goRootVF != null ? ImmutableList.of(goRootVF) : ImmutableList.of();
  }

  @Override
  public Collection<VirtualFile> getGoPathBinRoots(
      @Nullable Project project, @Nullable Module module) {
    return ImmutableList.of();
  }

  @Override
  public boolean isExternal() {
    // This is supposed to add the root as a GOPATH library, but since we put the library
    // under the project data directory, which is already added as a content root, this does
    // nothing.
    return true;
  }

  @Nullable
  static File getGoRoot(Project project) {
    BlazeImportSettings importSettings =
        BlazeImportSettingsManager.getInstance(project).getImportSettings();
    return importSettings != null
        ? new File(importSettings.getProjectDataDirectory(), ".gopath")
        : null;
  }

  public static void handleGoSymlinks(
      BlazeContext context, Project project, BlazeProjectData projectData) {
    Scope.push(
        context,
        (childContext) -> {
          childContext.push(new TimingScope("BuildGoSymbolicLinks", EventType.Other));
          createGoPathSourceRoot(project, projectData);
        });
  }

  /**
   * Creates the .gopath root under the project data directory. Then {@link #createSymLinks} for
   * each go target discovered in the target map into the root directory.
   */
  static synchronized void createGoPathSourceRoot(Project project, BlazeProjectData projectData) {
    File goRoot = getGoRoot(project);
    if (goRoot == null) {
      return;
    }
    FileOperationProvider provider = FileOperationProvider.getInstance();
    if (provider.exists(goRoot)) {
      try {
        provider.deleteRecursively(goRoot);
      } catch (IOException e) {
        logger.error(e);
        return;
      }
    }
    if (!provider.mkdirs(goRoot)) {
      logger.error("Failed to create " + goRoot);
      return;
    }
    ArtifactLocationDecoder decoder = projectData.getArtifactLocationDecoder();
    for (TargetIdeInfo target : projectData.getTargetMap().targets()) {
      if (target.getGoIdeInfo() == null || target.getGoIdeInfo().getImportPath() == null) {
        continue;
      }
      String importPath = target.getGoIdeInfo().getImportPath();
      createSymLinks(goRoot, importPath, getGoSources(target, decoder, projectData.getBlazeInfo()));
    }
  }

  /**
   * Creates a directory at [goRoot]/[importPath], and create symlinks in the directory for each
   * file in sources. This will create a structure that looks like a regular go project.
   *
   * <p>E.g., if we have sources foo/bar/a.go and foo/bar/baz/b.go, this will create:
   *
   * <pre>{@code
   * [goRoot]/[importPath]/a_[hash of foo/bar].go
   * [goRoot]/[importPath]/b_[hash of foo/bar/baz].go
   * }</pre>
   *
   * @return the newly created directory: [goRoot]/[importPath]
   */
  @Nullable
  public static File createSymLinks(Project project, String importPath, List<File> sources) {
    File goRoot = getGoRoot(project);
    if (goRoot == null) {
      return null;
    }
    return createSymLinks(goRoot, importPath, sources);
  }

  @Nullable
  private static synchronized File createSymLinks(
      File goRoot, String importPath, List<File> sources) {
    FileOperationProvider provider = FileOperationProvider.getInstance();
    File goPackage = new File(goRoot, importPath);
    if (!provider.exists(goPackage)) {
      if (!provider.mkdirs(goPackage)) {
        return null;
      }
    }
    for (File src : sources) {
      File link = new File(goPackage, hashName(src));
      if (!provider.exists(link)) {
        try {
          provider.createSymbolicLink(link, src);
        } catch (IOException e) {
          logger.warn(e);
        }
      }
    }
    return goPackage;
  }

  private static String hashName(File src) {
    String name = src.getName();
    return FileUtilRt.getNameWithoutExtension(name)
        + "_"
        + Integer.toHexString(src.getParent().hashCode())
        + "."
        + FileUtilRt.getExtension(name);
  }

  private static ImmutableList<File> getGoSources(
      TargetIdeInfo target, ArtifactLocationDecoder decoder, BlazeInfo blazeInfo) {
    if (target.getKind().equals(Kind.GO_WRAP_CC)) {
      return ImmutableList.of(getWrapCcGoFile(target, blazeInfo));
    }
    return ImmutableList.copyOf(decoder.decodeAll(target.getGoIdeInfo().getSources()));
  }

  private static File getWrapCcGoFile(TargetIdeInfo target, BlazeInfo blazeInfo) {
    String blazePackage = target.getKey().getLabel().blazePackage().relativePath();
    File directory = new File(blazeInfo.getGenfilesDirectory(), blazePackage);
    String filename = blazePackage + '/' + target.getKey().getLabel().targetName() + ".go";
    filename = filename.replace("_", "__");
    filename = filename.replace('/', '_');
    return new File(directory, filename);
  }

  public static Map<String, TargetKey> getPackageToTargetMap(Project project) {
    return SyncCache.getInstance(project)
        .get(
            PACKAGE_TO_TARGET_KEY,
            (p, pd) -> {
              Map<String, TargetKey> map = Maps.newHashMap();
              pd.getTargetMap().targets().stream()
                  .filter(t -> t.getGoIdeInfo() != null)
                  .filter(t -> t.getGoIdeInfo().getImportPath() != null)
                  .forEach(t -> map.putIfAbsent(t.getGoIdeInfo().getImportPath(), t.getKey()));
              return map;
            });
  }
}
