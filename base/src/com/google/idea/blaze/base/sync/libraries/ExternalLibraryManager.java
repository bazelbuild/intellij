/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.sync.libraries;

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.io.VfsUtils;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.prefetch.PrefetchIndexingTask;
import com.google.idea.blaze.base.prefetch.PrefetchService;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.sync.SyncListener;
import com.google.idea.blaze.base.sync.SyncMode;
import com.google.idea.blaze.base.sync.SyncResult;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.ide.FrameStateListener;
import com.intellij.ide.FrameStateManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider;
import com.intellij.openapi.roots.SyntheticLibrary;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.File;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/** Updates {@link BlazeExternalSyntheticLibrary}s after sync and on frame activation. */
public class ExternalLibraryManager {
  private static final BoolExperiment reindexExternalSyntheticLibraryAfterUpdate =
      new BoolExperiment("reindex.external.synthetic.library.after.update", true);

  private final Project project;
  private volatile ImmutableMap<Class<? extends BlazeExternalLibraryProvider>, LibraryState>
      libraries;

  private static class LibraryState {
    BlazeExternalLibraryProvider provider;
    BlazeExternalSyntheticLibrary library;
    ImmutableList<File> files;

    LibraryState(BlazeExternalLibraryProvider provider, ImmutableList<File> files) {
      this.provider = provider;
      this.library = new BlazeExternalSyntheticLibrary(provider.getLibraryName());
      this.files = files;
    }
  }

  public static ExternalLibraryManager getInstance(Project project) {
    return ServiceManager.getService(project, ExternalLibraryManager.class);
  }

  ExternalLibraryManager(Project project) {
    this.project = project;
    this.libraries = ImmutableMap.of();
    FrameStateListener listener =
        new FrameStateListener() {
          @Override
          public void onFrameActivated() {
            ApplicationManager.getApplication()
                .executeOnPooledThread(ExternalLibraryManager.this::updateLibraries);
          }
        };
    FrameStateManager.getInstance().addListener(listener);
    Disposer.register(project, () -> FrameStateManager.getInstance().removeListener(listener));
  }

  public ImmutableList<SyntheticLibrary> getLibrary(
      Class<? extends BlazeExternalLibraryProvider> providerClass) {
    LibraryState state = libraries.get(providerClass);
    return state != null ? ImmutableList.of(state.library) : ImmutableList.of();
  }

  private void initialize(BlazeProjectData projectData) {
    ImmutableMap.Builder<Class<? extends BlazeExternalLibraryProvider>, LibraryState> builder =
        ImmutableMap.builder();
    for (AdditionalLibraryRootsProvider provider :
        AdditionalLibraryRootsProvider.EP_NAME.getExtensionList()) {
      if (!(provider instanceof BlazeExternalLibraryProvider)) {
        continue;
      }
      BlazeExternalLibraryProvider blazeProvider = (BlazeExternalLibraryProvider) provider;
      ImmutableList<File> files = blazeProvider.getLibraryFiles(project, projectData);
      if (!files.isEmpty()) {
        builder.put(blazeProvider.getClass(), new LibraryState(blazeProvider, files));
      }
    }
    libraries = builder.build();
    updateLibraries();
  }

  private void updateLibraries() {
    ImmutableMap<Class<? extends BlazeExternalLibraryProvider>, LibraryState> libraries =
        this.libraries;
    if (libraries.isEmpty()) {
      return;
    }
    Future<?> future =
        PrefetchIndexingTask.submitPrefetchingTaskAndWait(
            project,
            PrefetchService.getInstance()
                .prefetchFiles(
                    libraries.values().stream()
                        .map(state -> state.files)
                        .flatMap(Collection::stream)
                        .collect(ImmutableList.toImmutableList()),
                    false,
                    false),
            "Prefetching external library files");
    try {
      future.get();
    } catch (InterruptedException | ExecutionException ignored) {
      // ignored
    }
    boolean updated = false;
    for (LibraryState state : libraries.values()) {
      ImmutableSet<VirtualFile> updatedFiles =
          state.files.stream()
              .map(VfsUtils::resolveVirtualFile)
              .filter(Objects::nonNull)
              .filter(VirtualFile::isValid)
              .collect(toImmutableSet());
      BlazeExternalSyntheticLibrary library = state.library;
      if (!updatedFiles.equals(library.getSourceRoots())) {
        library.updateFiles(updatedFiles);
        updated = true;
      }
    }
    if (updated) {
      reindexRoots();
    }
  }

  private void reindexRoots() {
    if (!reindexExternalSyntheticLibraryAfterUpdate.getValue()) {
      return;
    }
    TransactionGuard.submitTransaction(
        project,
        () ->
            WriteAction.run(
                () ->
                    ProjectRootManagerEx.getInstanceEx(project)
                        .makeRootsChange(EmptyRunnable.INSTANCE, false, true)));
  }

  static class Listener implements SyncListener {
    @Override
    public void onSyncComplete(
        Project project,
        BlazeContext context,
        BlazeImportSettings importSettings,
        ProjectViewSet projectViewSet,
        BlazeProjectData blazeProjectData,
        SyncMode syncMode,
        SyncResult syncResult) {
      if (syncMode == SyncMode.NO_BUILD
          && !ExternalLibraryManager.getInstance(project).libraries.isEmpty()) {
        return;
      }
      ApplicationManager.getApplication()
          .executeOnPooledThread(
              () -> ExternalLibraryManager.getInstance(project).initialize(blazeProjectData));
    }
  }
}
