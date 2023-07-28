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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.android.ide.common.util.PathString;
import com.android.projectmodel.ExternalAndroidLibrary;
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
import com.google.idea.blaze.android.sync.qsync.AndroidExternalLibraryManager;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.model.BlazeLibrary;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.qsync.ArtifactTracker;
import com.google.idea.blaze.base.qsync.QuerySync;
import com.google.idea.blaze.base.qsync.QuerySyncManager;
import com.google.idea.blaze.base.sync.SyncCache;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.libraries.BlazeLibraryCollector;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.stream.Stream;
import org.jetbrains.annotations.Nullable;

/** Blaze implementation of {@link AndroidModuleSystem}. */
public class BlazeModuleSystem extends BlazeModuleSystemBase {

  private static final Logger logger = Logger.getInstance(BlazeModuleSystem.class);
  private AndroidExternalLibraryManager androidExternalLibraryManager = null;

  BlazeModuleSystem(Module module) {
    super(module);
    if (QuerySync.isEnabled()) {
      androidExternalLibraryManager =
          new AndroidExternalLibraryManager(
              () -> {
                if (!QuerySyncManager.getInstance(project).isProjectLoaded()) {
                  return ImmutableList.of();
                }
                ArtifactTracker artifactTracker =
                    QuerySyncManager.getInstance(module.getProject()).getArtifactTracker();
                Path aarDirectory = artifactTracker.getExternalAarDirectory();
                // This can be called by the IDE as the user navigates the project and so might be
                // called before a sync has been completed and the project structure has been set
                // up.
                if (!aarDirectory.toFile().exists()) {
                  logger.warn("Aar library directory not created yet");
                  return ImmutableList.of();
                }
                try (Stream<Path> stream = Files.list(aarDirectory)) {
                  return stream.collect(toImmutableList());
                } catch (IOException ioe) {
                  throw new UncheckedIOException("Could not list aars", ioe);
                }
              });
    }
  }

  public Collection<ExternalAndroidLibrary> getDependentLibraries() {
    if (QuerySync.isEnabled()) {
      return androidExternalLibraryManager.getExternalLibraries();
    }
    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();

    if (blazeProjectData == null) {
      return ImmutableList.of();
    }

    if (isWorkspaceModule) {
      return SyncCache.getInstance(project)
          .get(BlazeModuleSystem.class, BlazeModuleSystem::getLibrariesForWorkspaceModule);
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

    ImmutableList.Builder<ExternalAndroidLibrary> libraries = ImmutableList.builder();
    ArtifactLocationDecoder decoder = blazeProjectData.getArtifactLocationDecoder();
    ExternalLibraryInterner externalLibraryInterner = ExternalLibraryInterner.getInstance(project);
    for (String libraryKey : registry.get(module).resourceLibraryKeys) {
      ImmutableMap<String, AarLibrary> aarLibraries = androidSyncData.importResult.aarLibraries;
      ExternalAndroidLibrary externalLibrary =
          toExternalLibrary(project, aarLibraries.get(libraryKey), decoder);
      if (externalLibrary != null) {
        libraries.add(externalLibraryInterner.intern(externalLibrary));
      }
    }
    return libraries.build();
  }

  private static ImmutableList<ExternalAndroidLibrary> getLibrariesForWorkspaceModule(
      Project project, BlazeProjectData blazeProjectData) {
    ArtifactLocationDecoder decoder = blazeProjectData.getArtifactLocationDecoder();
    ExternalLibraryInterner externalLibraryInterner = ExternalLibraryInterner.getInstance(project);
    ImmutableList.Builder<ExternalAndroidLibrary> libraries = ImmutableList.builder();
    for (BlazeLibrary library :
        BlazeLibraryCollector.getLibraries(
            ProjectViewManager.getInstance(project).getProjectViewSet(), blazeProjectData)) {
      if (library instanceof AarLibrary) {
        ExternalAndroidLibrary externalLibrary =
            toExternalLibrary(project, (AarLibrary) library, decoder);
        if (externalLibrary != null) {
          libraries.add(externalLibraryInterner.intern(externalLibrary));
        }
      }
    }
    return libraries.build();
  }

  @Nullable
  static ExternalAndroidLibrary toExternalLibrary(
      Project project, @Nullable AarLibrary library, ArtifactLocationDecoder decoder) {
    if (library == null) {
      return null;
    }
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

  @Override
  public Collection<ExternalAndroidLibrary> getAndroidLibraryDependencies(
      DependencyScopeType dependencyScopeType) {
    if (dependencyScopeType == DependencyScopeType.MAIN) {
      return getDependentLibraries();
    } else {
      return Collections.emptyList();
    }
  }
}
