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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos;
import com.google.idea.blaze.base.ideinfo.ProtoWrapper;

/**
 * Represents a Bazel build configuration from the Build Event Protocol (BEP).
 *
 * <p>Configurations contain platform-specific build information including CPU architecture,
 * platform name, make variables, and whether the configuration is used for building tools vs targets.
 */
@AutoValue
public abstract class BlazeConfiguration implements ProtoWrapper<BuildEventStreamProtos.Configuration> {

  public abstract String mnemonic();

  public abstract String platformName();

  public abstract String cpu();

  public abstract ImmutableMap<String, String> makeVariables();

  public abstract boolean isToolConfiguration();

  public static BlazeConfiguration fromProto(BuildEventStreamProtos.Configuration proto) {
    return builder()
        .setMnemonic(proto.getMnemonic())
        .setPlatformName(proto.getPlatformName())
        .setCpu(proto.getCpu())
        .setMakeVariables(ImmutableMap.copyOf(proto.getMakeVariableMap()))
        .setIsToolConfiguration(proto.getIsTool())
        .build();
  }

  @Override
  public BuildEventStreamProtos.Configuration toProto() {
    return BuildEventStreamProtos.Configuration.newBuilder()
        .setMnemonic(mnemonic())
        .setPlatformName(platformName())
        .setCpu(cpu())
        .putAllMakeVariable(makeVariables())
        .setIsTool(isToolConfiguration())
        .build();
  }

  public static Builder builder() {
    return new AutoValue_BlazeConfiguration.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setMnemonic(String mnemonic);

    public abstract Builder setPlatformName(String platformName);

    public abstract Builder setCpu(String cpu);

    public abstract Builder setMakeVariables(ImmutableMap<String, String> makeVariables);

    public abstract Builder setIsToolConfiguration(boolean isToolConfiguration);

    public abstract BlazeConfiguration build();
  }
}
