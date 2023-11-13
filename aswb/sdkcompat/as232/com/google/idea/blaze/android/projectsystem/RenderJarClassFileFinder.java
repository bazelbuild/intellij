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

import static java.util.stream.Collectors.joining;

import com.android.tools.idea.projectsystem.ClassFileFinder;
import com.android.tools.idea.projectsystem.ClassFileFinderUtil;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.android.libraries.RenderJarCache;
import com.google.idea.blaze.android.sync.model.AndroidResourceModule;
import com.google.idea.blaze.android.sync.model.AndroidResourceModuleRegistry;
import com.google.idea.blaze.android.targetmaps.TargetToBinaryMap;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.io.VirtualFileSystemProvider;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.qsync.QuerySync;
import com.google.idea.blaze.base.qsync.QuerySyncManager;
import com.google.idea.blaze.base.qsync.RenderJarArtifactTracker;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings.ProjectType;
import com.google.idea.blaze.base.sync.BlazeSyncModificationTracker;
import com.google.idea.blaze.base.sync.data.BlazeDataStorage;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.File;
import java.util.regex.Pattern;
import org.jetbrains.annotations.Nullable;

/**
 * A {@link ClassFileFinder} that uses deploy JAR like artifacts (called render jar henceforth) for
 * class files.
 *
 * <p>The render JAR contains all runtime dependencies of a binary target.
 *
 * <p>The Blaze targets that go into creating a resource module is known. Consequently, it is
 * possible to determine which binaries in the projectview depend on the resource declaring blaze
 * targets that constitutes the module. This class calculates the binary targets and attempts to
 * find classes from the render JARs.
 *
 * <p>This only works for resource modules (i.e. not the .workspace module). For .workspace module,
 * we try to find the class in all binary targets in projectview
 *
 * <p>NOTE: Blaze targets that constitutes the resource module will be called "resource target(s)"
 * in comments below.
 */
public class RenderJarClassFileFinder implements ClassFileFinder {
  /** Experiment to control whether class file finding from render jars should be enabled. */
  private static final BoolExperiment enabled =
      new BoolExperiment("aswb.renderjar.cff.enabled.3", true);

  /**
   * Experiment to toggle whether resource resolution is allowed from Render JARs. Render JARs
   * should not resolve resources by default.
   */
  @VisibleForTesting
  static final BoolExperiment resolveResourceClasses =
      new BoolExperiment("aswb.resolve.resources.render.jar", false);

  private static final Logger log = Logger.getInstance(RenderJarClassFileFinder.class);

  private static final String INTERNAL_PACKAGE = "_layoutlib_._internal_.";

  // matches foo.bar.R or foo.bar.R$baz
  private static final Pattern RESOURCE_CLASS_NAME = Pattern.compile(".+\\.R(\\$[^.]+)?$");

  private final Module module;
  private final Project project;

  // tracks the binary targets that depend resource targets
  // will be recalculated after every sync
  private ImmutableSet<TargetKey> binaryTargets = ImmutableSet.of();

  // tracks the value of {@link BlazeSyncModificationTracker} when binaryTargets is calculated
  // binaryTargets is calculated when the value of {@link BlazeSyncModificationTracker} does not
  // equal lastSyncCount
  long lastSyncCount = -1;

  // true if the current module is the .workspace Module
  private final boolean isWorkspaceModule;

  public RenderJarClassFileFinder(Module module) {
    this.module = module;
    this.project = module.getProject();
    this.isWorkspaceModule = BlazeDataStorage.WORKSPACE_MODULE_NAME.equals(module.getName());
  }

  @Nullable
  @Override
  public VirtualFile findClassFile(String fqcn) {
    if (!isEnabled()) {
      return null;
    }

    // Ever since Compose support was introduced in AS, finding class files is invoked during the
    // normal course of opening an editor. The contract for this method requires that it shouldn't
    // throw any exceptions, but we've had a few bugs where this method threw an exception, which
    // resulted in users not being able to open Kotlin files at all. In order to avoid this
    // scenario, we wrap the underlying call and ensure that no exceptions are thrown.
    try {
      return findClass(fqcn);
    } catch (Error e) {
      log.warn(
          String.format(
              "Unexpected error while finding the class file for `%1$s`: %2$s",
              fqcn, Throwables.getRootCause(e).getMessage()));
      return null;
    }
  }

  @Nullable
  public VirtualFile findClass(String fqcn) {
    // Render JAR should not resolve any resources. All resources should be available to the IDE
    // through ResourceRepository. Attempting to resolve resources from Render JAR indicates that
    // ASwB hasn't properly set up resources for the project.
    if (isResourceClass(fqcn) && !resolveResourceClasses.getValue()) {
      log.warn(String.format("Attempting to load resource '%s' from RenderJAR.", fqcn));
      return null;
    }

    if (Blaze.getProjectType(project).equals(ProjectType.QUERY_SYNC)) {
      return findClassQuerySync(fqcn);
    }
    return findClassLegacySync(fqcn);
  }

  private VirtualFile findClassQuerySync(String fqcn) {
    if (QuerySync.isComposeEnabled(project)) {
      RenderJarArtifactTracker renderJarArtifactTracker =
          QuerySyncManager.getInstance(project).getRenderJarArtifactTracker();
      // TODO(b/283280194): Setup fqcn -> target and target -> Render jar mappings to avoid
      // iterating over all render jars when trying to locate class for fqcn.
      // TODO(b/284002836): Collect metrics on time taken to iterate over the jars
      for (File renderJar : renderJarArtifactTracker.getRenderJars()) {
        VirtualFile renderResolveJarVf =
            VirtualFileSystemProvider.getInstance().getSystem().findFileByIoFile(renderJar);
        if (renderResolveJarVf != null) {
          return findClassInJar(renderResolveJarVf, fqcn);
        }
      }
      log.warn(String.format("Could not find class `%1$s` with Query Sync", fqcn));
    }
    return null;
  }

  private VirtualFile findClassLegacySync(String fqcn) {
    BlazeProjectData projectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (projectData == null) {
      log.warn("Could not find BlazeProjectData for project " + project.getName());
      return null;
    }

    ImmutableSet<TargetKey> binaryTargets = getBinaryTargets();
    if (binaryTargets.isEmpty()) {
      log.warn(
          String.format(
              "No binaries for module %s. Adding a binary target to the projectview and resyncing"
                  + " might fix the issue.",
              module.getName()));
      return null;
    }

    // Remove internal package prefix if present
    fqcn = StringUtil.trimStart(fqcn, INTERNAL_PACKAGE);

    // Look through render resolve JARs of the binaries that depend on the given
    // androidResourceModule. One androidResourceModule can comprise of multiple resource targets.
    // The binaries can depend on any subset of these resource targets. Generally, we only
    // expect one, or a small number of binaries here.
    for (TargetKey binaryTarget : binaryTargets) {
      VirtualFile classFile = getClassFromRenderResolveJar(projectData, fqcn, binaryTarget);
      if (classFile != null) {
        return classFile;
      }
    }
    log.warn(String.format("Could not find class `%1$s` (module: `%2$s`)", fqcn, module.getName()));
    return null;
  }

  @VisibleForTesting
  static boolean isResourceClass(String fqcn) {
    return RESOURCE_CLASS_NAME.matcher(fqcn).matches();
  }

  /**
   * Returns the cached list of binary targets that depend on resource targets. The cache is
   * recalculated if the project has been synced since last calculation
   */
  private ImmutableSet<TargetKey> getBinaryTargets() {
    long currentSyncCount =
        BlazeSyncModificationTracker.getInstance(project).getModificationCount();
    if (currentSyncCount == lastSyncCount) {
      // Return the cached set if there hasn't been a sync since last calculation
      return binaryTargets;
    }
    lastSyncCount = currentSyncCount;

    AndroidResourceModule androidResourceModule =
        AndroidResourceModuleRegistry.getInstance(project).get(module);
    if (androidResourceModule != null) {
      binaryTargets =
          TargetToBinaryMap.getInstance(project)
              .getBinariesDependingOn(androidResourceModule.sourceTargetKeys);
    } else if (isWorkspaceModule) {
      binaryTargets = TargetToBinaryMap.getInstance(project).getSourceBinaryTargets();
    } else {
      binaryTargets = ImmutableSet.of();
      log.warn("Could not find AndroidResourceModule for " + module.getName());
    }
    log.info(
        String.format(
            "Binary targets for module `%1$s`: %2$s",
            module.getName(),
            binaryTargets.stream()
                .limit(5)
                .map(t -> t.getLabel().toString())
                .collect(joining(", "))));
    return binaryTargets;
  }

  /**
   * Returns class file for fqcn if found in the render JAR corresponding to {@code binaryTarget}.
   * Returns null if something goes wrong or if render JAR does not contain fqcn
   */
  @Nullable
  private VirtualFile getClassFromRenderResolveJar(
      BlazeProjectData projectData, String fqcn, TargetKey binaryTarget) {
    TargetIdeInfo ideInfo = projectData.getTargetMap().get(binaryTarget);
    if (ideInfo == null) {
      return null;
    }

    File renderResolveJarFile =
        RenderJarCache.getInstance(project)
            .getCachedJarForBinaryTarget(projectData.getArtifactLocationDecoder(), ideInfo);

    if (renderResolveJarFile == null) {
      return null;
    }

    VirtualFile renderResolveJarVF =
        VirtualFileSystemProvider.getInstance().getSystem().findFileByIoFile(renderResolveJarFile);
    if (renderResolveJarVF == null) {
      return null;
    }

    return findClassInJar(renderResolveJarVF, fqcn);
  }

  @Nullable
  private static VirtualFile findClassInJar(final VirtualFile classJar, String fqcn) {
    VirtualFile jarRoot = getJarRootForLocalFile(classJar);
    if (jarRoot == null) {
      return null;
    }
    return ClassFileFinderUtil.findClassFileInOutputRoot(jarRoot, fqcn);
  }

  /** Test aware method to redirect JARs to {@link VirtualFileSystemProvider} for tests */
  private static VirtualFile getJarRootForLocalFile(VirtualFile file) {
    return ApplicationManager.getApplication().isUnitTestMode()
        ? VirtualFileSystemProvider.getInstance()
            .getSystem()
            .findFileByPath(file.getPath() + JarFileSystem.JAR_SEPARATOR)
        : JarFileSystem.getInstance().getJarRootForLocalFile(file);
  }

  public static boolean isEnabled() {
    return enabled.getValue();
  }
}
