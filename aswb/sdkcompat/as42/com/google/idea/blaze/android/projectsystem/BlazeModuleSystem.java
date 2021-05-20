/*
 * Copyright 2020 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.projectsystem;

import com.android.ide.common.util.PathString;
import com.android.projectmodel.ExternalLibrary;
import com.android.projectmodel.ExternalLibraryImpl;
import com.android.projectmodel.SelectiveResourceFolder;
import com.android.tools.idea.projectsystem.AndroidModuleSystem;
import com.android.tools.idea.projectsystem.DependencyScopeType;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.android.libraries.UnpackedAars;
import com.google.idea.blaze.android.sync.model.AarLibrary;
import com.google.idea.blaze.android.sync.model.AndroidResourceModuleRegistry;
import com.google.idea.blaze.android.sync.model.BlazeAndroidSyncData;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.model.BlazeLibrary;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.sync.SyncCache;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.libraries.BlazeLibraryCollector;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.java.libraries.JarCache;
import com.google.idea.blaze.java.sync.model.BlazeJarLibrary;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.File;
import java.util.Collection;
import org.jetbrains.annotations.Nullable;

/** Blaze implementation of {@link AndroidModuleSystem}. */
public class BlazeModuleSystem extends BlazeModuleSystemBase implements BlazeClassFileFinder {
  BlazeModuleSystem(Module module) {
    super(module);
  }

  @Override
  public boolean shouldSkipResourceRegistration() {
    return classFileFinder.shouldSkipResourceRegistration();
  }

  @Override
  @Nullable
  public VirtualFile findClassFile(String fqcn) {
    return classFileFinder.findClassFile(fqcn);
  }

  @Override
  public Collection<ExternalLibrary> getResolvedLibraryDependencies() {
    return getDependentLibraries();
  }

  // @Override
  public Collection<ExternalLibrary> getResolvedDependentLibraries() {
    return getDependentLibraries();
  }

  // @Override #as42: Method added in AS 203
  public Collection<ExternalLibrary> getResolvedLibraryDependencies(
      DependencyScopeType dependencyScopeType) {
    return getDependentLibraries();
  }

  public Collection<ExternalLibrary> getDependentLibraries() {
    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();

    if (blazeProjectData == null) {
      return ImmutableList.of();
    }

    if (isWorkspaceModule) {
      if (cacheLibraryComputation.getValue()) {
        return SyncCache.getInstance(project)
            .get(BlazeModuleSystem.class, BlazeModuleSystem::getLibrariesForWorkspaceModule);
      } else {
        return getLibrariesForWorkspaceModule(project, blazeProjectData);
      }
    }

    AndroidResourceModuleRegistry registry = AndroidResourceModuleRegistry.getInstance(project);
    TargetIdeInfo target = blazeProjectData.getTargetMap().get(registry.getTargetKey(module));
    if (target == null) {
      // this can happen if the module points to the <android-resources>, <project-data-dir>
      // <project-data-dir> does not contain any resource
      // <android-resources> contains all external resources as module's local resources, so there's
      // no dependent libraries
      return ImmutableList.of();
    }

    BlazeAndroidSyncData androidSyncData =
        blazeProjectData.getSyncState().get(BlazeAndroidSyncData.class);
    if (androidSyncData == null) {
      return ImmutableList.of();
    }

    ImmutableList.Builder<ExternalLibrary> libraries = ImmutableList.builder();
    ArtifactLocationDecoder decoder = blazeProjectData.getArtifactLocationDecoder();
    ExternalLibraryInterner externalLibraryInterner = ExternalLibraryInterner.getInstance(project);
    for (String libraryKey : registry.get(module).resourceLibraryKeys) {
      ImmutableMap<String, AarLibrary> aarLibraries = androidSyncData.importResult.aarLibraries;
      if (aarLibraries != null && aarLibraries.containsKey(libraryKey)) {
        ExternalLibrary externalLibrary =
            toExternalLibrary(project, aarLibraries.get(libraryKey), decoder);
        if (externalLibrary != null) {
          libraries.add(externalLibraryInterner.intern(externalLibrary));
        }
      }
    }
    return libraries.build();
  }

  private static ImmutableList<ExternalLibrary> getLibrariesForWorkspaceModule(
      Project project, BlazeProjectData blazeProjectData) {
    ArtifactLocationDecoder decoder = blazeProjectData.getArtifactLocationDecoder();
    ExternalLibraryInterner externalLibraryInterner = ExternalLibraryInterner.getInstance(project);
    ImmutableList.Builder<ExternalLibrary> libraries = ImmutableList.builder();
    for (BlazeLibrary library :
        BlazeLibraryCollector.getLibraries(
            ProjectViewManager.getInstance(project).getProjectViewSet(), blazeProjectData)) {
      if (library instanceof AarLibrary) {
        ExternalLibrary externalLibrary = toExternalLibrary(project, (AarLibrary) library, decoder);
        if (externalLibrary != null) {
          libraries.add(externalLibraryInterner.intern(externalLibrary));
        }
      } else if (library instanceof BlazeJarLibrary) {
        ExternalLibrary externalLibrary =
            toExternalLibrary(project, (BlazeJarLibrary) library, decoder);
        if (externalLibrary != null) {
          libraries.add(externalLibraryInterner.intern(externalLibrary));
        }
      }
    }
    return libraries.build();
  }

  @Nullable
  static ExternalLibrary toExternalLibrary(
      Project project, AarLibrary library, ArtifactLocationDecoder decoder) {
    UnpackedAars unpackedAars = UnpackedAars.getInstance(project);
    File aarFile = unpackedAars.getAarDir(decoder, library);
    if (aarFile == null) {
      logger.warn(
          String.format(
              "Fail to locate AAR file %s. Re-sync the project may solve the problem",
              library.aarArtifact));
      return null;
    }
    File resFolder = unpackedAars.getResourceDirectory(decoder, library);
    PathString resFolderPathString = resFolder == null ? null : new PathString(resFolder);
    return new ExternalLibraryImpl(library.key.toString())
        .withLocation(new PathString(aarFile))
        .withManifestFile(
            resFolderPathString == null
                ? null
                : resFolderPathString.getParentOrRoot().resolve("AndroidManifest.xml"))
        .withResFolder(
            resFolderPathString == null
                ? null
                : new SelectiveResourceFolder(resFolderPathString, null))
        .withSymbolFile(
            resFolderPathString == null
                ? null
                : resFolderPathString.getParentOrRoot().resolve("R.txt"))
        .withPackageName(library.resourcePackage);
  }

  @Nullable
  static ExternalLibrary toExternalLibrary(
      Project project, BlazeJarLibrary library, ArtifactLocationDecoder decoder) {
    File cachedJar = JarCache.getInstance(project).getCachedJar(decoder, library);
    if (cachedJar == null) {
      logger.warn(
          String.format(
              "Failed to locate jar file %s. Re-sync project may solve the problem", library));
      return null;
    }
    return new ExternalLibraryImpl(library.toString())
        .withClassJars(ImmutableList.of(new PathString(cachedJar)));
  }
}
