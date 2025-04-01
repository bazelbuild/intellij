/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.cpp;

import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.XMap;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

@State(name = "BlazeCTargetInfoService", storages = @Storage(value = "blazeTargetInfo.xml", roamingType = RoamingType.DISABLED))
final public class BlazeCTargetInfoService implements PersistentStateComponent<BlazeCTargetInfoService> {

  final public static Logger LOG = Logger.getInstance(BlazeCTargetInfoService.class);

  public static class TargetInfo {

    @Attribute
    private String compilerVersion;
    @Attribute
    private String configurationId;

    private TargetInfo() {
    }

    public TargetInfo(@NotNull String compilerVersion, @NotNull String configurationId) {
      this.compilerVersion = compilerVersion;
      this.configurationId = configurationId;
    }

    public String getCompilerVersion() {
      return compilerVersion;
    }

    public String getConfigurationId() {
      return configurationId;
    }
  }

  @XMap
  final private Map<String, TargetInfo> infoMap = new HashMap<>();

  @Override
  public BlazeCTargetInfoService getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull BlazeCTargetInfoService state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  public static void setState(Project project, ImmutableMap<TargetKey, TargetInfo> targetInfoMap) {
    final var service = project.getService(BlazeCTargetInfoService.class);

    service.infoMap.clear();
    targetInfoMap.forEach((key, info) -> {
      service.infoMap.put(key.getLabel().toString(), info);
    });
  }

  public static @Nullable TargetInfo get(Project project, TargetExpression key) {
    final var service = project.getService(BlazeCTargetInfoService.class);

    final var result = service.infoMap.get(key.toString());
    if (result == null) {
      LOG.warn(String.format("could not find target info for target %s", key));
    }

    return result;
  }

  public static @Nullable TargetInfo getFirst(Project project, Iterable<TargetExpression> keys) {
    final var service = project.getService(BlazeCTargetInfoService.class);

    for (final var key : keys) {
      final var result = service.infoMap.get(key.toString());
      if (result != null) {
        return result;
      }
    }

    LOG.warn(String.format("could not find target info for any target in %s", keys));
    return null;
  }
}
