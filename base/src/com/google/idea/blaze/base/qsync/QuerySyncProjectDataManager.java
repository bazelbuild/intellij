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

import com.google.common.base.Preconditions;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.sync.data.BlazeDataStorage;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.common.Context;
import com.google.idea.blaze.common.PrintOutput;
import com.google.idea.blaze.qsync.BlazeProjectListener;
import com.google.idea.blaze.qsync.project.BlazeProjectSnapshot;
import com.google.idea.blaze.qsync.project.PostQuerySyncData;
import com.google.idea.blaze.qsync.project.ProjectDefinition;
import com.google.idea.blaze.qsync.project.SnapshotDeserializer;
import com.google.idea.blaze.qsync.project.SnapshotSerializer;
import com.intellij.openapi.diagnostic.Logger;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Optional;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import javax.annotation.Nullable;

/**
 * Implementation of {@link BlazeProjectDataManager} specific to querysync.
 *
 * <p>TODO: it's not yet clear how useful this class is for querysync. This is currently a pragmatic
 * approach to get more IDE functionality working with querysync. The ideal long term design is not
 * yet determined.
 */
public class QuerySyncProjectDataManager implements BlazeProjectDataManager, BlazeProjectListener {

  private final Logger logger = Logger.getInstance(getClass());

  private final ProjectDeps.Builder projectDepsBuilder;
  private volatile ProjectDeps projectDeps;
  private volatile QuerySyncProjectData projectData;

  public QuerySyncProjectDataManager(ProjectDeps.Builder projectDepsBuilder) {
    this.projectDepsBuilder = projectDepsBuilder;
  }

  private synchronized void ensureProjectDepsCreated(BlazeContext context) {
    if (projectDeps == null) {
      projectDeps = projectDepsBuilder.build(context);
      projectData =
          new QuerySyncProjectData(
              projectDeps.workspacePathResolver(), projectDeps.workspaceLanguageSettings());
    }
  }

  @Override
  public void graphCreated(Context context, BlazeProjectSnapshot instance) {
    Preconditions.checkNotNull(projectData);
    projectData = projectData.withSnapshot(instance);
    try {
      writeToDisk(instance);
    } catch (IOException ioe) {
      context.output(PrintOutput.error("Failed to save project data: %s", ioe.getMessage()));
      context.setHasError();
      logger.error("Failed to save project state", ioe);
    }
  }

  synchronized ProjectDefinition getProjectDefinition(BlazeContext context) {
    ensureProjectDepsCreated(context);
    return projectDeps.projectDefinition();
  }

  @Nullable
  @Override
  public QuerySyncProjectData getBlazeProjectData() {
    return projectData;
  }

  public Optional<PostQuerySyncData> loadFromDisk(BlazeContext context) {
    File f = getSnapshotFile();
    if (!f.exists()) {
      return Optional.empty();
    }
    ensureProjectDepsCreated(context);
    try (InputStream in = new GZIPInputStream(new FileInputStream(f))) {
      return Optional.of(new SnapshotDeserializer().readFrom(in).getSyncData());
    } catch (IOException e) {
      logger.error("Failed to load project state", e);
      return Optional.empty();
    }
  }

  private void writeToDisk(BlazeProjectSnapshot snapshot) throws IOException {
    File f = getSnapshotFile();
    if (!f.getParentFile().exists()) {
      if (!f.getParentFile().mkdirs()) {
        throw new IOException("Cannot create directory " + f.getParent());
      }
    }
    try (OutputStream o = new GZIPOutputStream(new FileOutputStream(f))) {
      new SnapshotSerializer().visit(snapshot.queryData()).toProto().writeTo(o);
    }
  }

  private File getSnapshotFile() {
    BlazeImportSettings importSettings =
        BlazeImportSettingsManager.getInstance(projectDepsBuilder.getProject()).getImportSettings();
    return new File(BlazeDataStorage.getProjectDataDir(importSettings), "qsyncdata.gz");
  }

  @Nullable
  @Override
  public BlazeProjectData loadProject(BlazeImportSettings importSettings) {
    // this is only call from legacy sync codepaths (so should never be called, since this class
    // is not used in that case).
    // TODO(mathewi) Tidy up the interface to remove this unnecessary stuff.
    throw new UnsupportedOperationException();
  }

  @Override
  public void saveProject(BlazeImportSettings importSettings, BlazeProjectData projectData) {
    // this is only call from legacy sync codepaths (so should never be called, since this class
    // is not used in that case).
    // TODO(mathewi) Tidy up the interface to remove this unnecessary stuff.
    throw new UnsupportedOperationException();
  }

}
