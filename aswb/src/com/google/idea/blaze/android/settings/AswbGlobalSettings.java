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
package com.google.idea.blaze.android.settings;

import com.intellij.openapi.components.*;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.Nullable;

/**
 * Stores aswb global settings.
 */
@State(
  name = "AswbGlobalSettings",
  storages = @Storage(file = StoragePathMacros.APP_CONFIG + "/aswb.global.xml")
)
public class AswbGlobalSettings implements PersistentStateComponent<AswbGlobalSettings> {

  private String localSdkLocation;

  public static AswbGlobalSettings getInstance() {
    return ServiceManager.getService(AswbGlobalSettings.class);
  }

  @Nullable
  @Override
  public AswbGlobalSettings getState() {
    return this;
  }

  @Override
  public void loadState(AswbGlobalSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  public void setLocalSdkLocation(String localSdkLocation) {
    this.localSdkLocation = localSdkLocation;
  }

  public String getLocalSdkLocation() {
    return localSdkLocation;
  }
}
