/*
 * Copyright 2026 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.model;

import com.google.common.collect.ImmutableMap;
import com.google.devtools.intellij.model.ProjectData;
import com.google.idea.blaze.base.ideinfo.ProtoWrapper;
import javax.annotation.Nullable;

/**
 * Container for Bazel build configuration data extracted from the Build Event Protocol (BEP).
 *
 * <p>Stores a map of configuration IDs to their corresponding configuration details.
 */
public final class BlazeConfigurationData implements ProtoWrapper<ProjectData.BlazeConfigurationData> {

  public final ImmutableMap<String, BlazeConfiguration> configurations;

  public static final BlazeConfigurationData EMPTY = new BlazeConfigurationData(ImmutableMap.of());

  public static BlazeConfigurationData create(ImmutableMap<String, BlazeConfiguration> configurations) {
    return new BlazeConfigurationData(configurations);
  }

  BlazeConfigurationData(ImmutableMap<String, BlazeConfiguration> configurations) {
    this.configurations = configurations;
  }

  @Override
  public ProjectData.BlazeConfigurationData toProto() {
    final var builder = ProjectData.BlazeConfigurationData.newBuilder();
    configurations.forEach((id, config) -> builder.putConfigurations(id, config.toProto()));
    return builder.build();
  }

  public static BlazeConfigurationData fromProto(ProjectData.BlazeConfigurationData proto) {
    final var builder = ImmutableMap.<String, BlazeConfiguration>builder();
    proto.getConfigurationsMap().forEach((id, config) -> builder.put(id, BlazeConfiguration.fromProto(config)));

    return create(builder.build());
  }

  @Nullable
  public BlazeConfiguration get(String configId) {
    return configurations.get(configId);
  }
}
