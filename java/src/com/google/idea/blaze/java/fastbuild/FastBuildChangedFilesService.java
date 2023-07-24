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
package com.google.idea.blaze.java.fastbuild;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.collect.Sets.intersection;
import static com.google.common.util.concurrent.MoreExecutors.listeningDecorator;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableSet;
import com.google.common.graph.SuccessorsFunction;
import com.google.common.graph.Traverser;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.java.fastbuild.FastBuildState.BuildOutput;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.serviceContainer.NonInjectable;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.messages.MessageBusConnection;
import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.annotation.concurrent.GuardedBy;

/**
 * Monitors the IntelliJ VFS for changed files so that the fast build system can only recompile
 * those.
 *
 * <p>When a build is launched (via {@link #newBuild}, we don't know what source files are required
 * by this target, so we just start storing <em>all</em> changed Java files. Once the build
 * completes, we throw away any changed files that aren't source files for this build and from that
 * point on, we ignore changed files that aren't part of the build.
 *
 * <p>If we get more than {@link #MAX_FILES_TO_COLLECT} we just tell the caller to trigger a new
 * build with Blaze.
 */
final class FastBuildChangedFilesService implements Disposable {

  static FastBuildChangedFilesService getInstance(Project project) {
    return project.getService(FastBuildChangedFilesService.class);
  }

  private static final Logger logger = Logger.getInstance(FastBuildChangedFilesService.class);

  @VisibleForTesting static final int MAX_FILES_TO_COLLECT = 30;

  private final Project project;
  private final BlazeProjectDataManager projectDataManager;
  private final ListeningExecutorService executor;

  @GuardedBy("this")
  private final Map<Label, Data> labelData = new HashMap<>();

  @GuardedBy("this")
  private boolean subscribed;

  FastBuildChangedFilesService(Project project) {
    this(
        project,
        BlazeProjectDataManager.getInstance(project),
        listeningDecorator(
            ConcurrencyUtil.newSingleThreadExecutor(
                FastBuildChangedFilesService.class.getSimpleName() + "-" + project.getName())));
  }

  @NonInjectable
  private FastBuildChangedFilesService(
      Project project,
      BlazeProjectDataManager projectDataManager,
      ListeningExecutorService executor) {
    this.project = project;
    this.projectDataManager = projectDataManager;
    this.executor = executor;
  }

  static FastBuildChangedFilesService createForTests(
      Project project,
      BlazeProjectDataManager projectDataManager,
      ListeningExecutorService executor) {
    return new FastBuildChangedFilesService(project, projectDataManager, executor);
  }

  @Override
  public void dispose() {
    executor.shutdown();
  }

  synchronized void newBuild(Label label, ListenableFuture<BuildOutput> buildFuture) {

    if (!subscribed) {
      subscribe();
    }

    labelData.put(label, Data.waitingForSources());

    Futures.addCallback(buildFuture, new DeployJarFinishedCallback(label), executor);
  }

  synchronized void resetBuild(Label label) {
    labelData.remove(label);
  }

  @AutoValue
  abstract static class ChangedSources {

    abstract boolean needsFullCompile();

    abstract ImmutableSet<File> changedSources();

    static ChangedSources fullCompile() {
      return new AutoValue_FastBuildChangedFilesService_ChangedSources(true, ImmutableSet.of());
    }

    private static ChangedSources withChangedSources(Set<File> changedSources) {
      return new AutoValue_FastBuildChangedFilesService_ChangedSources(
          false, ImmutableSet.copyOf(changedSources));
    }
  }

  synchronized ChangedSources getAndResetChangedSources(Label label) {

    Data data = labelData.get(label);
    checkState(data != null, "No build information about %s", label);

    switch (data.state) {
      case WAITING_FOR_SOURCES:
      case COLLECTING:
        ChangedSources result = ChangedSources.withChangedSources(data.changedSources);
        data.changedSources = new HashSet<>();
        return result;
      case TOO_MANY_CHANGES:
        // Don't reset anything in data; it'll get reset when a new build starts and newBuild() is
        // called.
        return ChangedSources.fullCompile();
    }
    throw new AssertionError("Unknown state " + data.state);
  }

  @SuppressWarnings("GuardedBy")
  synchronized void addFilesFromFailedCompilation(Label label, Set<File> files) {

    Data data = labelData.get(label);
    checkState(data != null, "No build information about %s", label);

    // TODO(b/145386688): Access should be guarded by enclosing instance
    // 'com.google.idea.blaze.java.fastbuild.FastBuildChangedFilesService' of 'data', which is not
    // accessible in this scope; instead found: 'this'
    data.updateChangedSources(files);
  }

  private synchronized void subscribe() {
    MessageBusConnection connection = project.getMessageBus().connect();
    connection.subscribe(VirtualFileManager.VFS_CHANGES, new ChangeSourceListener());
    subscribed = true;
  }

  private class DeployJarFinishedCallback implements FutureCallback<BuildOutput> {

    private final Label label;

    private DeployJarFinishedCallback(Label label) {
      this.label = label;
    }

    @Override
    @SuppressWarnings("GuardedBy")
    public void onSuccess(BuildOutput result) {

      ImmutableSet<File> targetSources = getSourceFiles(label, result.blazeData());

      synchronized (FastBuildChangedFilesService.this) {
        Data data = labelData.get(label);
        if (data != null) {
          // TODO(b/145386688): Access should be guarded by enclosing instance
          // 'com.google.idea.blaze.java.fastbuild.FastBuildChangedFilesService' of 'data', which is
          // not accessible in this scope; instead found: 'FastBuildChangedFilesService.this'
          data.setSources(targetSources);
        }
      }
    }

    @Override
    public void onFailure(Throwable t) {
      synchronized (FastBuildChangedFilesService.this) {
        resetBuild(label);
      }
    }
  }

  @SuppressWarnings("GuardedBy")
  private class ChangeSourceListener implements BulkFileListener {
    @Override
    public void after(List<? extends VFileEvent> events) {
      ListenableFuture<Void> submit =
          executor.submit(
              () -> {
                synchronized (FastBuildChangedFilesService.this) {
                  if (!isCollecting()) {
                    return null;
                  }

                  ImmutableSet<File> changedFiles =
                      events.stream()
                          // We don't want to compile deleted files
                          .filter(event -> !(event instanceof VFileDeleteEvent))
                          .map(VFileEvent::getPath)
                          .filter(f -> f.endsWith(".java"))
                          .map(File::new)
                          .collect(toImmutableSet());

                  if (changedFiles.isEmpty()) {
                    return null;
                  }
                  // TODO(b/145386688): Access should be guarded by enclosing instance
                  // 'com.google.idea.blaze.java.fastbuild.FastBuildChangedFilesService' of 'data',
                  // which is not accessible in this scope
                  labelData.values().forEach(data -> data.updateChangedSources(changedFiles));
                  return null;
                }
              });
      Futures.addCallback(submit, new LogErrorCallback(), executor);
    }

    @GuardedBy("FastBuildChangedFilesService.this")
    private boolean isCollecting() {
      return labelData.values().stream()
          .map(d -> d.state)
          .anyMatch(s -> s.equals(State.WAITING_FOR_SOURCES) || s.equals(State.COLLECTING));
    }

    private class LogErrorCallback implements FutureCallback<Void> {

      @Override
      public void onSuccess(Void result) {}

      @Override
      public void onFailure(Throwable t) {
        logger.warn("Error finding changed sources", t);
      }
    }
  }

  private ImmutableSet<File> getSourceFiles(Label label, Map<Label, FastBuildBlazeData> blazeData) {
    Stopwatch timer = Stopwatch.createStarted();
    ImmutableSet<File> sourceFiles = getSourceFilesRecursively(label, blazeData);
    long ms = timer.elapsed().toMillis();
    if (ms > 500) {
      logger.info("Collecting sources for " + label + " took " + ms + "ms");
    }
    return sourceFiles;
  }

  private ImmutableSet<File> getSourceFilesRecursively(
      Label label, Map<Label, FastBuildBlazeData> blazeData) {
    FastBuildBlazeData data = blazeData.get(label);
    if (data == null || !data.javaInfo().isPresent()) {
      return ImmutableSet.of();
    }
    Set<File> sourceFiles = new HashSet<>();
    ArtifactLocationDecoder decoder =
        projectDataManager.getBlazeProjectData().getArtifactLocationDecoder();
    SuccessorsFunction<FastBuildBlazeData> graph = l -> getDependencies(blazeData, l);
    Traverser.forGraph(graph)
        .breadthFirst(data)
        .forEach(
            d -> {
              d.javaInfo().get().sources().stream()
                  .map(decoder::decode)
                  .filter(f -> f.getName().endsWith(".java"))
                  .forEach(sourceFiles::add);
            });
    return ImmutableSet.copyOf(sourceFiles);
  }

  private static ImmutableSet<FastBuildBlazeData> getDependencies(
      Map<Label, FastBuildBlazeData> map, FastBuildBlazeData labelData) {
    return labelData.dependencies().stream()
        .map(map::get)
        .filter(Objects::nonNull)
        .filter(data -> data.javaInfo().isPresent())
        .collect(toImmutableSet());
  }

  private enum State {
    WAITING_FOR_SOURCES,
    COLLECTING,
    TOO_MANY_CHANGES
  }

  private static class Data {
    // State only moves forward, never backward (WAITING_FOR_SOURCES->COLLECTING->TOO_MANY_CHANGES)
    State state = State.WAITING_FOR_SOURCES;
    Set<File> changedSources = new HashSet<>();
    ImmutableSet<File> sources = null;

    static Data waitingForSources() {
      return new Data();
    }

    @GuardedBy("FastBuildChangedFilesService.this")
    void setSources(ImmutableSet<File> sources) {
      checkState(state.equals(State.WAITING_FOR_SOURCES));
      state = State.COLLECTING;
      this.sources = sources;
      ImmutableSet<File> allModifiedFiles = ImmutableSet.copyOf(changedSources);
      changedSources.clear();
      updateChangedSources(allModifiedFiles);
    }

    @GuardedBy("FastBuildChangedFilesService.this")
    void updateChangedSources(Set<File> changedFiles) {

      if (state.equals(State.TOO_MANY_CHANGES)) {
        return;
      }

      if (state.equals(State.WAITING_FOR_SOURCES)) {
        changedSources.addAll(changedFiles);
      } else if (state.equals(State.COLLECTING)) {
        changedSources.addAll(intersection(changedFiles, sources));
      }
      if (changedSources.size() > MAX_FILES_TO_COLLECT) {
        changedSources = ImmutableSet.of();
        state = State.TOO_MANY_CHANGES;
      }
    }
  }
}
