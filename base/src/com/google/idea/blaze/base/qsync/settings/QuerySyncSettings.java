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

import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.base.logging.LoggedSettingsProvider;
import com.google.idea.blaze.base.qsync.QuerySync;
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
  static class State {
    public boolean useQuerySync = false;

    public boolean showDetailedInformationInEditor = false;

    public boolean syncBeforeBuild = false;
  }

  private QuerySyncSettings.State state = new QuerySyncSettings.State();

  public static QuerySyncSettings getInstance() {
    return ApplicationManager.getApplication().getService(QuerySyncSettings.class);
  }

  public void enableUseQuerySync(boolean useQuerySync) {
    state.useQuerySync = useQuerySync;
  }

  /**
   * Gets current state.useQuerySync value. It's a field to store current user selection for
   * enabling query sync or not and it indicates whether query sync is enabled next time after IDE
   * get restart. But it should not be used to decide if query sync is enabled now. Refer to {@code
   * QuerySync#isEnable()} when you need to decide if a query sync feature is enabled or not.
   */
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

  /**
   * Implementation of {@link LoggedSettingsProvider} which provides query sync related settings to
   * logger.
   */
  public static class SettingsLogger implements LoggedSettingsProvider {
    private final Supplier<Boolean> isQuerySyncEnabled;

    public SettingsLogger() {
      this(QuerySync::isEnabled);
    }

    public SettingsLogger(Supplier<Boolean> isQuerySyncEnabled) {
      this.isQuerySyncEnabled = isQuerySyncEnabled;
    }

    @Override
    public String getNamespace() {
      return "QuerySyncSettings";
    }

    @Override
    public ImmutableMap<String, String> getApplicationSettings() {
      QuerySyncSettings settings = QuerySyncSettings.getInstance();

      ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
      // This settings can be empty (default value) for first time user, but they may have enabled
      // query sync via experiment value. So use QuerySync.isEnabled() instead of this setting data.
      // And QuerySync is enabled based on QuerySync.isEnabled() not only settings.useQuerySync().
      // Eventually users will stop using experiment value and settings.useQuerySync() would always
      // be the same as QuerySync.isEnabled().
      builder.put("useQuerySync", Boolean.toString(isQuerySyncEnabled.get()));
      builder.put(
          "showDetailedInformationInEditor",
          Boolean.toString(settings.showDetailedInformationInEditor()));
      builder.put("syncBeforeBuild", Boolean.toString(settings.syncBeforeBuild()));
      return builder.buildOrThrow();
    }
  }
}
