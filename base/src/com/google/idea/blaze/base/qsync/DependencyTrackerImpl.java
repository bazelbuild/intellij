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
package com.google.idea.blaze.base.qsync;

import static java.util.stream.Collectors.joining;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import com.google.common.util.concurrent.SettableFuture;
import com.google.common.util.concurrent.Uninterruptibles;
import com.google.idea.blaze.base.bazel.BazelExitCode;
import com.google.idea.blaze.base.logging.utils.querysync.BuildDepsStatsScope;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.common.PrintOutput;
import com.google.idea.blaze.common.PrintOutput.OutputType;
import com.google.idea.blaze.exception.BuildException;
import com.google.idea.blaze.qsync.BlazeProject;
import com.google.idea.blaze.qsync.project.BlazeProjectSnapshot;
import com.google.idea.blaze.qsync.project.ProjectDefinition;
import com.google.idea.blaze.qsync.project.ProjectDefinition.LanguageClass;
import com.google.idea.blaze.qsync.project.ProjectTarget;
import com.google.idea.blaze.qsync.project.TargetTree;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.openapi.vfs.newvfs.RefreshSession;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;

/**
 * A file that tracks what files in the project can be analyzed and what is the status of their
 * dependencies.
 */
public class DependencyTrackerImpl implements DependencyTracker {

  private final Project project;

  private final BlazeProject blazeProject;
  private final DependencyBuilder builder;
  private final ArtifactTracker artifactTracker;

  private static final Logger logger = Logger.getInstance(DependencyTrackerImpl.class);

  public DependencyTrackerImpl(
      Project project,
      BlazeProject blazeProject,
      DependencyBuilder builder,
      ArtifactTracker artifactTracker) {
    this.project = project;
    this.blazeProject = blazeProject;
    this.builder = builder;
    this.artifactTracker = artifactTracker;
  }

  /**
   * For a given project targets, returns all the targets outside the project that its source files
   * need to be edited fully. This method return the dependencies for the target with fewest pending
   * so that if dependencies have been built for one, the empty set will be returned even if others
   * have pending dependencies.
   */
  @Override
  @Nullable
  public Set<Label> getPendingExternalDeps(Set<Label> projectTargets) {
    Optional<BlazeProjectSnapshot> currentSnapshot = blazeProject.getCurrent();
    if (currentSnapshot.isEmpty()) {
      return null;
    }

    ImmutableList.Builder<ImmutableSet<Label>> targetSets = ImmutableList.builder();
    for (Label projectTarget : projectTargets) {
      ImmutableSet<Label> targets =
          currentSnapshot.get().graph().getTransitiveExternalDependencies(projectTarget);
      if (targets == null) {
        return null;
      }
      targetSets.add(targets);
    }

    Set<Label> cachedTargets = artifactTracker.getLiveCachedTargets();
    return targetSets.build().stream()
        .map(targets -> Sets.difference(targets, cachedTargets))
        .min(Comparator.comparingInt(SetView::size))
        .map(SetView::immutableCopy)
        .orElse(null);
  }

  /** Recursively get all the transitive deps outside the project */
  @Override
  @Nullable
  public Set<Label> getPendingTargets(Path workspaceRelativePath) {
    Preconditions.checkState(!workspaceRelativePath.isAbsolute(), workspaceRelativePath);

    Optional<BlazeProjectSnapshot> currentSnapshot = blazeProject.getCurrent();
    if (currentSnapshot.isEmpty()) {
      return null;
    }
    ImmutableSet<Label> owners = currentSnapshot.get().getTargetOwners(workspaceRelativePath);
    if (owners == null) {
      return null;
    }
    return getPendingExternalDeps(owners);
  }

  private BlazeProjectSnapshot getCurrentSnapshot() {
    return blazeProject
        .getCurrent()
        .orElseThrow(() -> new IllegalStateException("Sync is not yet complete"));
  }

  /**
   * Builds the external dependencies of the given targets, putting the resultant libraries in the
   * shared library directory so that they are picked up by the IDE.
   */
  @Override
  public boolean buildDependenciesForTargets(BlazeContext context, Set<Label> projectTargets)
      throws IOException, BuildException {
    BuildDepsStatsScope.fromContext(context)
        .ifPresent(stats -> stats.setRequestedTargets(projectTargets));
    BlazeProjectSnapshot snapshot = getCurrentSnapshot();

    Optional<RequestedTargets> maybeRequestedTargets =
        computeRequestedTargets(snapshot, projectTargets);
    if (maybeRequestedTargets.isEmpty()) {
      return false;
    }

    buildDependencies(context, snapshot, maybeRequestedTargets.get());
    return true;
  }

  /**
   * Builds the dependencies of the given target, putting the resultant libraries in the shared
   * library directory so that they are picked up by the IDE.
   */
  @Override
  public void buildDependenciesForTarget(BlazeContext context, Label target)
      throws IOException, BuildException {
    BlazeProjectSnapshot snapshot = getCurrentSnapshot();

    RequestedTargets requestedTargets =
        new RequestedTargets(ImmutableSet.of(target), ImmutableSet.of(target));
    buildDependencies(context, snapshot, requestedTargets);
  }

  private void buildDependencies(
      BlazeContext context, BlazeProjectSnapshot snapshot, RequestedTargets requestedTargets)
      throws IOException, BuildException {
    BuildDepsStatsScope.fromContext(context)
        .ifPresent(stats -> stats.setBuildTargets(requestedTargets.buildTargets));
    OutputInfo outputInfo =
        builder.build(
            context,
            requestedTargets.buildTargets,
            getTargetLanguages(snapshot, requestedTargets.buildTargets));
    reportErrorsAndWarnings(context, snapshot, outputInfo);

    ImmutableSet<Path> updatedFiles =
        updateCaches(context, requestedTargets.expectedDependencyTargets, outputInfo);
    refreshFiles(context, updatedFiles);
  }

  private static ImmutableSet<LanguageClass> getTargetLanguages(
      BlazeProjectSnapshot snapshot, ImmutableSet<Label> targets) {
    return targets.stream()
        .map(snapshot.graph().targetMap()::get)
        .map(ProjectTarget::languages)
        .reduce((a, b) -> Sets.union(a, b).immutableCopy())
        .orElse(ImmutableSet.of());
  }

  /**
   * Returns the list of project targets related to the given workspace paths.
   *
   * @param context Context
   * @param workspaceRelativePath Workspace relative path to find targets for. This may be a source
   *     file, directory or BUILD file.
   * @return Corresponding project targets. For a source file, this is the targets that build that
   *     file. For a BUILD file, it's the set or targets defined in that file. For a directory, it's
   *     the set of all targets defined in all build packages within the directory (recursively).
   */
  @Override
  public TargetsToBuild getProjectTargets(BlazeContext context, Path workspaceRelativePath) {
    return blazeProject
        .getCurrent()
        .map(snapshot -> getProjectTargets(context, snapshot, workspaceRelativePath))
        .orElse(TargetsToBuild.NONE);
  }

  @VisibleForTesting
  public static TargetsToBuild getProjectTargets(
      BlazeContext context, BlazeProjectSnapshot snapshot, Path workspaceRelativePath) {
    if (workspaceRelativePath.endsWith("BUILD")) {
      Path packagePath = workspaceRelativePath.getParent();
      return TargetsToBuild.targetGroup(snapshot.graph().allTargets().get(packagePath));
    } else {
      TargetTree targets = snapshot.graph().allTargets().getSubpackages(workspaceRelativePath);
      if (!targets.isEmpty()) {
        // this will only be non-empty for directories
        return TargetsToBuild.targetGroup(targets.toLabelSet());
      }
    }
    // Not a build file or a directory containing packages.
    if (snapshot.graph().getAllSourceFiles().contains(workspaceRelativePath)) {
      ImmutableSet<Label> targetOwner = snapshot.getTargetOwners(workspaceRelativePath);
      if (!targetOwner.isEmpty()) {
        return TargetsToBuild.forSourceFile(targetOwner);
      }
    } else {
      context.output(
          PrintOutput.error("Can't find any supported targets for %s", workspaceRelativePath));
      context.output(
          PrintOutput.error(
              "If this is a newly added supported rule, please re-sync your project."));
      context.setHasWarnings();
    }
    return TargetsToBuild.NONE;
  }

  public static Optional<RequestedTargets> computeRequestedTargets(
      BlazeProjectSnapshot snapshot, Set<Label> projectTargets) {
    ImmutableSet<Label> externalDeps =
        projectTargets.stream()
            .flatMap(t -> snapshot.graph().getTransitiveExternalDependencies(t).stream())
            .collect(ImmutableSet.toImmutableSet());

    return Optional.of(new RequestedTargets(ImmutableSet.copyOf(projectTargets), externalDeps));
  }

  private void reportErrorsAndWarnings(
      BlazeContext context, BlazeProjectSnapshot snapshot, OutputInfo outputInfo)
      throws NoDependenciesBuiltException {
    if (outputInfo.isEmpty()) {
      throw new NoDependenciesBuiltException(
          "Build produced no usable outputs. Please fix any build errors and retry.");
    }

    if (!outputInfo.getTargetsWithErrors().isEmpty()) {
      ProjectDefinition projectDefinition = snapshot.queryData().projectDefinition();
      context.setHasWarnings();
      ImmutableListMultimap<Boolean, Label> targetsByInclusion =
          Multimaps.index(outputInfo.getTargetsWithErrors(), projectDefinition::isIncluded);
      if (targetsByInclusion.containsKey(false)) {
        ImmutableList<?> errorTargets = targetsByInclusion.get(false);
        context.output(
            PrintOutput.error(
                "%d external %s had build errors: \n  %s",
                errorTargets.size(),
                StringUtil.pluralize("dependency", errorTargets.size()),
                errorTargets.stream().limit(10).map(Object::toString).collect(joining("\n  "))));
        if (errorTargets.size() > 10) {
          context.output(PrintOutput.log("and %d more.", errorTargets.size() - 10));
        }
      }
      if (targetsByInclusion.containsKey(true)) {
        ImmutableList<?> errorTargets = targetsByInclusion.get(true);
        context.output(
            PrintOutput.output(
                "%d project %s had build errors: \n  %s",
                errorTargets.size(),
                StringUtil.pluralize("target", errorTargets.size()),
                errorTargets.stream().limit(10).map(Object::toString).collect(joining("\n  "))));
        if (errorTargets.size() > 10) {
          context.output(PrintOutput.log("and %d more.", errorTargets.size() - 10));
        }
      }
    } else if (outputInfo.getExitCode() != BazelExitCode.SUCCESS) {
      // This will happen if there is an error in a build file, as no build actions are attempted
      // in that case.
      context.setHasWarnings();
      context.output(PrintOutput.error("There were build errors."));
    }
    if (context.hasWarnings()) {
      context.output(
          PrintOutput.error(
              "Your dependencies may be incomplete. If you see unresolved symbols, please fix the"
                  + " above build errors and try again."));
      context.setHasWarnings();
    }
  }

  /**
   * Returns a list of local cache files that build by target provided. Returns Optional.empty() if
   * the target has not yet been built.
   */
  @Override
  public Optional<ImmutableSet<Path>> getCachedArtifacts(Label target) {
    return artifactTracker.getCachedFiles(target);
  }

  private ImmutableSet<Path> updateCaches(
      BlazeContext context, Set<Label> targets, OutputInfo outputInfo) throws BuildException {
    return artifactTracker.update(targets, outputInfo, context).updatedFiles();
  }

  private void refreshFiles(BlazeContext context, ImmutableSet<Path> updatedFiles) {
    ApplicationEx applicationEx = ApplicationManagerEx.getApplicationEx();
    //noinspection UnstableApiUsage
    applicationEx.assertIsNonDispatchThread();
    context.output(
        new PrintOutput(
            String.format("Refreshing virtual file system... (%d files)", updatedFiles.size())));
    markExistingFilesDirty(context, updatedFiles);
    ImmutableList.Builder<VirtualFile> virtualFiles = ImmutableList.builder();
    applicationEx.invokeAndWait(
        () -> {
          final boolean unused =
              applicationEx.runWriteActionWithNonCancellableProgressInDispatchThread(
                  "Finding build outputs",
                  project,
                  null,
                  indicator -> {
                    ProjectRootManagerEx.getInstanceEx(project)
                        .mergeRootsChangesDuring(
                            () -> {
                              // Finding a virtual file that is not yet in the VFS runs a refresh
                              // session and triggers virtual file system changed events. Having
                              // multiple changed events in the same project root, like currently
                              // .dependencies dependency is, causes inefficient O(n^2) project
                              // structure refreshing.
                              //
                              // Bring new files to the VFS by refreshing their parents only. Do
                              // refreshing in two stages: (1) find parents and (2) rescan and
                              // refresh them from a background thread (involves files changed
                              // events being fired in the EDT).
                              //
                              // Considering the current artifact directories are almost flat it is
                              // not more expensive than refreshing specific files only. This action
                              // needs to run in a write action as in rare cases (initialization or
                              // after some directories where manually deleted) some parents may
                              // need to be refreshed first and it might actually be expensive in
                              // the later case.
                              virtualFiles.addAll(
                                  getFileParentsAsVirtualFilesMarkedDirty(context, updatedFiles));
                            });
                  });
        });
    refreshFilesRecursively(virtualFiles.build());
    context.output(
        new PrintOutput(
            String.format(
                "Done refreshing virtual file system... (%d files)", updatedFiles.size())));
  }

  private static void refreshFilesRecursively(ImmutableList<VirtualFile> virtualFiles) {
    SettableFuture<Boolean> done = SettableFuture.create();
    try {
      RefreshSession refreshSession =
          RefreshQueue.getInstance().createSession(true, true, () -> done.set(true));
      refreshSession.addAllFiles(virtualFiles);
      refreshSession.launch();
      Uninterruptibles.getUninterruptibly(done);
    } catch (ExecutionException e) {
      throw new IllegalStateException(e);
    }
  }

  private static ImmutableList<VirtualFile> getFileParentsAsVirtualFilesMarkedDirty(
      BlazeContext context, ImmutableSet<Path> updatedFiles) {
    final ImmutableList.Builder<VirtualFile> virtualFiles = ImmutableList.builder();
    ImmutableList<Path> paths =
        updatedFiles.stream()
            .map(Path::getParent)
            .distinct()
            .collect(ImmutableList.toImmutableList());
    for (final Path path : paths) {
      VirtualFile virtualFile = VfsUtil.findFileByIoFile(path.toFile(), true);
      if (virtualFile != null) {
        if (virtualFile instanceof NewVirtualFile) {
          ((NewVirtualFile) virtualFile).markDirty();
        } else {
          // This is unexpected. Send details back to us.
          logger.error(
              String.format("Unknown virtual file class %s for %s.", virtualFile.getClass(), path),
              new Throwable());
        }
        virtualFiles.add(virtualFile);
      } else {
        context.output(new PrintOutput("Cannot find: " + path, OutputType.ERROR));
      }
    }
    return virtualFiles.build();
  }

  /**
   * Marks any existing artifact files as dirty.
   *
   * <p>The virtual file system relies on file watchers to discover files that have changed. Those
   * that are known to have possibly changed are refreshed during virtual file system rescan
   * sessions, which are initiated by calls to `LocalFileSystem.refreshFiles` and similar.
   *
   * <p>Since file watchers are asynchronous it might happen that by this point the IDE does not yet
   * know that existing artifact files have changed. This method marks any existing files from
   * {@code updatedFiles} to make sure that later refreshing of the virtual file system rescans
   * existing files.
   */
  private static void markExistingFilesDirty(
      BlazeContext context, ImmutableSet<Path> updatedFiles) {
    int markedAsDirty = 0;
    for (final Path path : updatedFiles) {
      VirtualFile virtualFile = getFileByIoFileIfInVfs(path);
      if (virtualFile != null) {
        if (virtualFile instanceof NewVirtualFile) {
          ((NewVirtualFile) virtualFile).markDirty();
          markedAsDirty++;
        } else {
          // This is unexpected. Send details back to us.
          logger.error(
              String.format("Unknown virtual file class %s for %s.", virtualFile.getClass(), path),
              new Throwable());
        }
      }
    }
    context.output(
        new PrintOutput(String.format("%d existing files require refreshing...", markedAsDirty)));
  }

  /**
   * Returns a virtual file by its IO path if it is already known by the VFS.
   *
   * <p>This method does not attempt to bring to the VFS files that are not yet there and thus does
   * not cause any VFS level file-change events and does not need to run in a write action.
   */
  private static VirtualFile getFileByIoFileIfInVfs(Path path) {
    return VfsUtil.findFileByIoFile(path.toFile(), false /* refreshIfNeeded */);
  }
}
