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

import com.google.idea.blaze.base.lang.buildfile.language.BuildFileLanguage;
import com.google.idea.blaze.base.lang.buildfile.language.BuildFileTypeFactory;
import com.google.idea.blaze.base.sync.status.BlazeSyncStatus;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

/**
 * Stores blaze view settings.
 */
@State(
  name = "BlazeUserSettings",
  storages = @Storage(id = "other", value = "blaze.view.xml")
)
public class BlazeUserSettings implements PersistentStateComponent<BlazeUserSettings> {

  public boolean suppressConsoleForRunAction = false;
  private boolean resyncAutomatically = false;
  private boolean buildFileSupportEnabled = true;
  private boolean syncStatusPopupShown = false;
  private boolean expandSyncToWorkingSet = true;
  private boolean showPerformanceWarnings = false;
  private boolean attachSourcesByDefault = false;
  private boolean attachSourcesOnDemand = false;
  private boolean collapseProjectView = true;
  private String blazeBinaryPath = "/usr/bin/blaze";
  @Nullable private String bazelBinaryPath;

  @NotNull
  public static BlazeUserSettings getInstance() {
    return ServiceManager.getService(BlazeUserSettings.class);
  }

  @Override
  @NotNull
  public BlazeUserSettings getState() {
    return this;
  }

  @Override
  public void loadState(BlazeUserSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  /**
   * Also kicks off an incremental sync if we're now syncing automatically,
   * and the project is currently dirty.
   */
  public void setResyncAutomatically(boolean resyncAutomatically) {
    if (this.resyncAutomatically == resyncAutomatically) {
      return;
    }
    this.resyncAutomatically = resyncAutomatically;
    ProjectManager projectManager = ApplicationManager.getApplication().getComponent(ProjectManager.class);
    Project[] openProjects = projectManager.getOpenProjects();
    for (Project project : openProjects) {
      if (Blaze.isBlazeProject(project)) {
        BlazeSyncStatus.getInstance(project).queueAutomaticSyncIfDirty();
      }
    }
  }

  public boolean getResyncAutomatically() {
    return resyncAutomatically;
  }

  /**
   * Triggers an update to the list of registered file types.
   */
  public void setBuildFileSupportEnabled(boolean supportEnabled) {
    if (supportEnabled != buildFileSupportEnabled) {
      buildFileSupportEnabled = supportEnabled;
      BuildFileTypeFactory.updateBuildFileLanguageEnabled(BuildFileLanguage.BUILD_FILE_SUPPORT_ENABLED.getValue() && supportEnabled);
    }
  }

  public boolean getBuildFileSupportEnabled() {
    return buildFileSupportEnabled;
  }

  public boolean getSuppressConsoleForRunAction() {
    return suppressConsoleForRunAction;
  }

  public void setSuppressConsoleForRunAction(boolean suppressConsoleForRunAction) {
    this.suppressConsoleForRunAction = suppressConsoleForRunAction;
  }

  public boolean getSyncStatusPopupShown() {
    return syncStatusPopupShown;
  }

  public void setSyncStatusPopupShown(boolean syncStatusPopupShown) {
    this.syncStatusPopupShown = syncStatusPopupShown;
  }

  public boolean getExpandSyncToWorkingSet() {
    return expandSyncToWorkingSet;
  }

  public void setExpandSyncToWorkingSet(boolean expandSyncToWorkingSet) {
    this.expandSyncToWorkingSet = expandSyncToWorkingSet;
  }

  public boolean getShowPerformanceWarnings() {
    return showPerformanceWarnings;
  }

  public void setShowPerformanceWarnings(boolean showPerformanceWarnings) {
    this.showPerformanceWarnings = showPerformanceWarnings;
  }

  public String getBlazeBinaryPath() {
    return blazeBinaryPath;
  }

  public void setBlazeBinaryPath(String blazeBinaryPath) {
    this.blazeBinaryPath = blazeBinaryPath;
  }

  @Nullable
  public String getBazelBinaryPath() {
    return bazelBinaryPath;
  }

  public void setBazelBinaryPath(String bazelBinaryPath) {
    this.bazelBinaryPath = bazelBinaryPath;
  }

  public boolean getAttachSourcesByDefault() {
    return attachSourcesByDefault;
  }

  public void setAttachSourcesByDefault(boolean attachSourcesByDefault) {
    this.attachSourcesByDefault = attachSourcesByDefault;
  }

  public boolean getAttachSourcesOnDemand() {
    return attachSourcesOnDemand;
  }

  public void setAttachSourcesOnDemand(boolean attachSourcesOnDemand) {
    this.attachSourcesOnDemand = attachSourcesOnDemand;
  }

  public boolean getCollapseProjectView() {
    return collapseProjectView;
  }

  public void setCollapseProjectView(boolean collapseProjectView) {
    this.collapseProjectView = collapseProjectView;
  }
}