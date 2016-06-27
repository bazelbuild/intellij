/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.settings;

import com.google.common.collect.Lists;
import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * Manages storage for the project's {@link BlazeImportSettings}.
 */
@State(
  name = "BlazeSettings",
  storages = {@Storage(file = StoragePathMacros.PROJECT_FILE),
    @Storage(file = StoragePathMacros.PROJECT_CONFIG_DIR + "/blaze.xml",
      scheme = StorageScheme.DIRECTORY_BASED)}
)
public class BlazeImportSettingsManager implements
                                        PersistentStateComponent<BlazeImportSettingsManager.State> {

  @Nullable
  private BlazeImportSettings importSettings;

  @NotNull
  private Project project;

  public BlazeImportSettingsManager(@NotNull Project project) {
    this.project = project;
  }

  @NotNull
  public static BlazeImportSettingsManager getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, BlazeImportSettingsManager.class);
  }

  @SuppressWarnings("unchecked")
  @Nullable
  @Override
  public State getState() {
    State state = new State();
    List<BlazeImportSettings> value = Lists.newArrayList();
    if (importSettings != null) {
      value.add(importSettings);
    }
    state.setLinkedExternalProjectsSettings(value);
    return state;
  }

  @Override
  public void loadState(State state) {
    Collection<BlazeImportSettings> settings = state.getLinkedExternalProjectsSettings();
    if (settings != null && !settings.isEmpty()) {
      importSettings = settings.iterator().next();
    }
    else {
      importSettings = null;
    }
  }

  @Nullable
  public BlazeImportSettings getImportSettings() {
    return importSettings;
  }

  public void setImportSettings(@NotNull BlazeImportSettings importSettings) {
    this.importSettings = importSettings;
  }

  /**
   * State class for the Blaze settings.
   */
  static class State {

    private List<BlazeImportSettings> importSettings = Lists.newArrayList();

    @AbstractCollection(surroundWithTag = false, elementTypes = {BlazeImportSettings.class})
    public List<BlazeImportSettings> getLinkedExternalProjectsSettings() {
      return importSettings;
    }

    public void setLinkedExternalProjectsSettings(List<BlazeImportSettings> settings) {
      importSettings = settings;
    }
  }
}