/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.qsync.settings;

import com.google.common.base.Suppliers;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import java.util.function.Supplier;

/** The settings for query sync to be stored per user. */
@State(
    name = "QuerySyncSettings",
    storages = {@Storage("query.sync.user.settings.xml")})
public class QuerySyncSettings implements PersistentStateComponent<QuerySyncSettings.State> {
  // If enabled query Sync via a legacy way (set up experimental value).
  // Only read the initial value, as the sync mode should not change over a single run of the IDE.
  private static final Supplier<Boolean> QUERY_SYNC_ENABLED_LEGACY =
      Suppliers.memoize(new BoolExperiment("use.query.sync", false)::getValue);

  // If enabled sync before build for Query Sync via a legacy way (set up experimental value)
  private static final Supplier<Boolean> SYNC_BEFORE_BUILD_ENABLED_LEGACY =
      Suppliers.memoize(new BoolExperiment("query.sync.before.build", false)::getValue);

  static class State {
    public boolean useQuerySync = QUERY_SYNC_ENABLED_LEGACY.get();

    public boolean showDetailedInformationInEditor = true;

    public boolean syncBeforeBuild = SYNC_BEFORE_BUILD_ENABLED_LEGACY.get();
  }

  private QuerySyncSettings.State state = new QuerySyncSettings.State();

  public static QuerySyncSettings getInstance() {
    return ApplicationManager.getApplication().getService(QuerySyncSettings.class);
  }

  public void enableUseQuerySync(boolean useQuerySync) {
    state.useQuerySync = useQuerySync;
  }

  public boolean useQuerySync() {
    return state.useQuerySync;
  }

  public void enableShowDetailedInformationInEditor(boolean showDetailedInformationInEditor) {
    state.showDetailedInformationInEditor = showDetailedInformationInEditor;
  }

  public boolean showDetailedInformationInEditor() {
    return state.showDetailedInformationInEditor;
  }

  public void enableSyncBeforeBuild(boolean syncBeforeBuild) {
    state.syncBeforeBuild = syncBeforeBuild;
  }

  public boolean syncBeforeBuild() {
    return state.syncBeforeBuild;
  }

  @Override
  public QuerySyncSettings.State getState() {
    return state;
  }

  @Override
  public void loadState(QuerySyncSettings.State state) {
    this.state = state;
  }

  public boolean enableQuerySyncByExperimentFile() {
    return QUERY_SYNC_ENABLED_LEGACY.get();
  }

  public boolean enableSyncBeforeBuildByExperimentFile() {
    return SYNC_BEFORE_BUILD_ENABLED_LEGACY.get();
  }
}
