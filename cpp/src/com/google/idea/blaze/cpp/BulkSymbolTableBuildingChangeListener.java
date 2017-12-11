/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.cpp;

import com.google.common.base.Stopwatch;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.sync.BlazeSyncParams.SyncMode;
import com.google.idea.blaze.base.sync.SyncListener;
import com.google.idea.blaze.base.sync.projectview.ImportRoots;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.concurrency.JobScheduler;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileCopyEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent;
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent;
import com.jetbrains.cidr.lang.symbols.symtable.OCSymbolTablesBuildingActivity;
import com.jetbrains.cidr.lang.symbols.symtable.SymbolTableProvider;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Rebuilds symbol tables if there are many file changes. Workaround (or partial solution?) for
 * b/63598392 and https://youtrack.jetbrains.com/issue/CPP-9805.
 *
 * <p>CMake workspaces have a file listener that can kick off symbol table building, so this does
 * something similar.
 *
 * <p>The problem is that FileSymbolTableUpdater's queues are not coordinated with symbol table
 * building, so it may still make a pass over a ton of files after symbol table building. That pass
 * has overhead checking cache hits.
 *
 * <p>Tries to match the events watched by FileSymbolTablesCache. That also tracks files outside of
 * the import roots, but we'll be conservative and track only files in the import roots.
 */
public class BulkSymbolTableBuildingChangeListener implements BulkFileListener {
  private static final Logger logger =
      Logger.getInstance(BulkSymbolTableBuildingChangeListener.class);

  private static final BoolExperiment enableExperiment =
      new BoolExperiment("cidr.symbol.table.file.listener", true);

  private static final int BULK_SIZE = 20;
  // Give it a bit of delay, in case the RefreshSessions are not notifying all changes at once.
  // We don't want to delay too much, otherwise a user can start attempting to use the IDE
  // before we get a chance to put it into dumb mode.
  private static final int DELAY_TIME_MS = 1500;

  private final Project project;
  private WorkspaceRoot workspaceRoot;
  private ImportRoots importRoots;
  private boolean enabled = false;

  private final Set<VirtualFile> queuedFiles = new HashSet<>();
  private final AtomicBoolean flushIsQueued = new AtomicBoolean(false);

  private static BulkSymbolTableBuildingChangeListener getInstance(Project project) {
    return ServiceManager.getService(project, BulkSymbolTableBuildingChangeListener.class);
  }

  BulkSymbolTableBuildingChangeListener(Project project) {
    this.project = project;
    project.getMessageBus().connect(project).subscribe(VirtualFileManager.VFS_CHANGES, this);
  }

  private void updateForBlazeProject(BlazeProjectData blazeProjectData) {
    workspaceRoot = WorkspaceRoot.fromProjectSafe(project);
    importRoots = ImportRoots.forProjectSafe(project);
    enabled =
        workspaceRoot != null
            && importRoots != null
            && blazeProjectData.workspaceLanguageSettings.isLanguageActive(LanguageClass.C)
            && enableExperiment.getValue();
  }

  @Override
  public void before(List<? extends VFileEvent> list) {}

  @Override
  public void after(List<? extends VFileEvent> events) {
    if (!enabled) {
      return;
    }
    for (VFileEvent event : events) {
      VirtualFile modifiedFile = null;
      // Skip delete events.
      if (event instanceof VFileContentChangeEvent || event instanceof VFileCreateEvent) {
        modifiedFile = event.getFile();
      } else if (event instanceof VFileCopyEvent) {
        VFileCopyEvent copyEvent = (VFileCopyEvent) event;
        modifiedFile = copyEvent.getNewParent();
      } else if (event instanceof VFileMoveEvent) {
        VFileMoveEvent moveEvent = (VFileMoveEvent) event;
        modifiedFile = moveEvent.getNewParent();
      } else if (event instanceof VFilePropertyChangeEvent) {
        VFilePropertyChangeEvent propEvent = (VFilePropertyChangeEvent) event;
        // Check for file renames (sometimes we get property change events without the name
        // actually changing though)
        if (propEvent.getPropertyName().equals(VirtualFile.PROP_NAME)
            && !propEvent.getOldValue().equals(propEvent.getNewValue())) {
          modifiedFile = propEvent.getFile();
        }
      }
      if (SymbolTableProvider.isSourceFile(modifiedFile)) {
        queueChange(modifiedFile);
      }
    }
  }

  private void queueChange(VirtualFile vf) {
    // Since we are working with VirtualFile, they are not necessarily part of the project.
    // Check that the file is in the import roots as a proxy for belonging to the project
    // (alternatively check if it's under the workspace root?).
    if (vf == null || !vf.isValid() || !isInImportRoots(vf)) {
      return;
    }
    queuedFiles.add(vf);
    if (!flushIsQueued.compareAndSet(false, true)) {
      return;
    }
    @SuppressWarnings("unused") // go/futurereturn-lsc
    Future<?> possiblyIgnoredError =
        JobScheduler.getScheduler()
            .schedule(
                () ->
                    ApplicationManager.getApplication()
                        .invokeLater(this::flushQueue, ModalityState.NON_MODAL),
                DELAY_TIME_MS,
                TimeUnit.MILLISECONDS);
  }

  private boolean isInImportRoots(VirtualFile vf) {
    WorkspacePath workspacePath = workspaceRoot.workspacePathForSafe(new File(vf.getPath()));
    if (workspacePath == null) {
      return false;
    }
    return importRoots.containsWorkspacePath(workspacePath);
  }

  private void flushQueue() {
    flushIsQueued.set(false);
    if (queuedFiles.size() < BULK_SIZE) {
      queuedFiles.clear();
      return;
    }
    List<VirtualFile> files = new ArrayList<>(queuedFiles);
    queuedFiles.clear();
    // FileSymbolTableUpdater splits by "root" and header, and only builds symbol
    // tables for roots, but we just do the same action for both for simplicity.
    logger.info(String.format("Rebuilding symbols for %d changed files", files.size()));
    Stopwatch stopwatch = Stopwatch.createStarted();
    OCSymbolTablesBuildingActivity.getInstance(project).buildSymbolsForFiles(files);
    DumbService.getInstance(project)
        .runWhenSmart(
            () ->
                logger.info(
                    String.format(
                        "Rebuilding symbols took %d s", stopwatch.elapsed(TimeUnit.SECONDS))));
  }

  /** Sync listener to check if the workspace still supports C. */
  public static class WorkspaceTypeSyncListener extends SyncListener.Adapter {

    @Override
    public void onSyncComplete(
        Project project,
        BlazeContext context,
        BlazeImportSettings importSettings,
        ProjectViewSet projectViewSet,
        BlazeProjectData blazeProjectData,
        SyncMode syncMode,
        SyncResult syncResult) {
      BulkSymbolTableBuildingChangeListener.getInstance(project)
          .updateForBlazeProject(blazeProjectData);
    }
  }
}
