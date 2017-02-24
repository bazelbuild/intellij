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

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.project.Project;
import javax.annotation.Nullable;
import org.jetbrains.annotations.NotNull;

/** Manages storage for the project's {@link BlazeImportSettings}. */
@State(name = "BlazeImportSettings", storages = @Storage(file = StoragePathMacros.WORKSPACE_FILE))
public class BlazeImportSettingsManager implements PersistentStateComponent<BlazeImportSettings> {

  @Nullable private BlazeImportSettings importSettings;

  public BlazeImportSettingsManager() {}

  public static BlazeImportSettingsManager getInstance(Project project) {
    return ServiceManager.getService(project, BlazeImportSettingsManager.class);
  }

  @SuppressWarnings("unchecked")
  @Nullable
  @Override
  public BlazeImportSettings getState() {
    return importSettings;
  }

  @Override
  public void loadState(BlazeImportSettings importSettings) {
    this.importSettings = importSettings;
  }

  @Nullable
  public BlazeImportSettings getImportSettings() {
    return importSettings;
  }

  public void setImportSettings(@NotNull BlazeImportSettings importSettings) {
    this.importSettings = importSettings;
  }
}
