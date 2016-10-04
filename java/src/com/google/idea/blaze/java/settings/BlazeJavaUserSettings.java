/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.java.settings;

import com.google.idea.blaze.base.bazel.BuildSystemProvider;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeUserSettings;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

/** Java-specific user settings. */
@State(name = "BlazeJavaUserSettings", storages = @Storage("blaze.java.user.settings.xml"))
public class BlazeJavaUserSettings implements PersistentStateComponent<BlazeJavaUserSettings> {
  private boolean useJarCache = getDefaultJarCacheValue();
  private boolean attachSourcesByDefault = false;
  private boolean attachSourcesOnDemand = false;
  private boolean migrated;

  public static BlazeJavaUserSettings getInstance() {
    BlazeJavaUserSettings settings = ServiceManager.getService(BlazeJavaUserSettings.class);
    settings.migrate();
    return settings;
  }

  private static boolean getDefaultJarCacheValue() {
    return BuildSystemProvider.defaultBuildSystem().buildSystem() == Blaze.BuildSystem.Blaze;
  }

  @Override
  @NotNull
  public BlazeJavaUserSettings getState() {
    return this;
  }

  @Override
  public void loadState(BlazeJavaUserSettings state) {
    XmlSerializerUtil.copyBean(state, this);
    migrate();
  }

  /**
   * Added in 1.8, can be removed ~2.2. When this is removed, java settings can no longer be
   * migrated. (This is non-catastrophic though -- the settings will just reset)
   */
  private void migrate() {
    if (!migrated) {
      BlazeUserSettings userSettings = BlazeUserSettings.getInstance();
      this.attachSourcesByDefault = userSettings.getAttachSourcesByDefault();
      this.attachSourcesOnDemand = userSettings.getAttachSourcesOnDemand();
      this.migrated = true;
    }
  }

  public boolean getUseJarCache() {
    return useJarCache;
  }

  public void setUseJarCache(boolean useJarCache) {
    this.useJarCache = useJarCache;
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

  @SuppressWarnings("unused") // Used by bean serialization
  public boolean getMigrated() {
    return migrated;
  }

  @SuppressWarnings("unused") // Used by bean serialization
  public void setMigrated(boolean migrated) {
    this.migrated = migrated;
  }
}
