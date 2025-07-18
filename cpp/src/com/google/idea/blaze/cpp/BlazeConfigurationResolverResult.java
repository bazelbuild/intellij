/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.ideinfo.CToolchainIdeInfo;

import java.nio.file.Path;
import java.util.Optional;
import javax.annotation.concurrent.Immutable;
import java.io.File;
import java.util.Map;

/**
 * Resolve configuration maps, etc. obtained from running the {@link BlazeConfigurationResolver}.
 */
@Immutable
final class BlazeConfigurationResolverResult {

  private final ImmutableMap<BlazeResolveConfigurationData, BlazeResolveConfiguration>
      uniqueResolveConfigurations;
  private final ImmutableMap<CToolchainIdeInfo, BlazeCompilerSettings> compilerSettings;
  private final ImmutableSet<Path> validHeaderRoots;
  private final Optional<XcodeCompilerSettings> xcodeProperties;

  private BlazeConfigurationResolverResult(
      ImmutableMap<BlazeResolveConfigurationData, BlazeResolveConfiguration> uniqueResolveConfigurations,
      ImmutableMap<CToolchainIdeInfo, BlazeCompilerSettings> compilerSettings,
      ImmutableSet<Path> validHeaderRoots,
      Optional<XcodeCompilerSettings> xcodeProperties) {
    this.uniqueResolveConfigurations = uniqueResolveConfigurations;
    this.compilerSettings = compilerSettings;
    this.validHeaderRoots = validHeaderRoots;
    this.xcodeProperties = xcodeProperties;
  }

  static Builder builder() {
    return new Builder();
  }

  static BlazeConfigurationResolverResult empty() {
    return builder().build();
  }

  ImmutableList<BlazeResolveConfiguration> getAllConfigurations() {
    return uniqueResolveConfigurations.values().asList();
  }

  ImmutableMap<BlazeResolveConfigurationData, BlazeResolveConfiguration> getConfigurationMap() {
    return uniqueResolveConfigurations;
  }

  ImmutableMap<CToolchainIdeInfo, BlazeCompilerSettings> getCompilerSettings() {
    return compilerSettings;
  }

  public Optional<XcodeCompilerSettings> getXcodeProperties() {
    return xcodeProperties;
  }

  boolean isValidHeaderRoot(File absolutePath) {
    return validHeaderRoots.contains(absolutePath.toPath());
  }

  boolean isEquivalentConfigurations(BlazeConfigurationResolverResult other) {
    if (!uniqueResolveConfigurations.keySet().equals(other.uniqueResolveConfigurations.keySet())) {
      return false;
    }
    for (Map.Entry<BlazeResolveConfigurationData, BlazeResolveConfiguration> mapEntry :
        uniqueResolveConfigurations.entrySet()) {
      BlazeResolveConfiguration config = mapEntry.getValue();
      BlazeResolveConfiguration otherConfig =
          other.uniqueResolveConfigurations.get(mapEntry.getKey());
      if (otherConfig == null || !config.isEquivalentConfigurations(otherConfig)) {
        return false;
      }
    }
    return validHeaderRoots.equals(other.validHeaderRoots) &&
        xcodeProperties.equals(other.xcodeProperties);
  }

  static class Builder {
    ImmutableMap<BlazeResolveConfigurationData, BlazeResolveConfiguration> uniqueConfigurations =
        ImmutableMap.of();
    ImmutableMap<CToolchainIdeInfo, BlazeCompilerSettings> compilerSettings = ImmutableMap.of();
    ImmutableSet<Path> validHeaderRoots = ImmutableSet.of();
    Optional<XcodeCompilerSettings> xcodeSettings = Optional.empty();

    public Builder() {}

    BlazeConfigurationResolverResult build() {
      return new BlazeConfigurationResolverResult(
          uniqueConfigurations, compilerSettings, validHeaderRoots,
          xcodeSettings);
    }

    void setUniqueConfigurations(
        ImmutableMap<BlazeResolveConfigurationData, BlazeResolveConfiguration>
            uniqueConfigurations) {
      this.uniqueConfigurations = uniqueConfigurations;
    }

    void setCompilerSettings(
        ImmutableMap<CToolchainIdeInfo, BlazeCompilerSettings> compilerSettings) {
      this.compilerSettings = compilerSettings;
    }

    void setValidHeaderRoots(ImmutableSet<Path> validHeaderRoots) {
      this.validHeaderRoots = validHeaderRoots;
    }

    public void setXcodeSettings(
        Optional<XcodeCompilerSettings> xcodeSettings) {
      this.xcodeSettings = xcodeSettings;
    }
  }
}
