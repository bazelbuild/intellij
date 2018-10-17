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
package com.google.idea.blaze.base.ideinfo;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.model.primitives.ExecutionRootPath;
import java.io.Serializable;

/** Represents toolchain info from a cc_toolchain or cc_toolchain_suite */
public final class CToolchainIdeInfo implements Serializable {
  private static final long serialVersionUID = 4L;

  private final ImmutableList<String> baseCompilerOptions;
  private final ImmutableList<String> cCompilerOptions;
  private final ImmutableList<String> cppCompilerOptions;
  private final ImmutableList<ExecutionRootPath> builtInIncludeDirectories;
  private final ExecutionRootPath cppExecutable;
  private final String targetName;

  private final ImmutableList<String> unfilteredCompilerOptions;
  private final ImmutableList<ExecutionRootPath> unfilteredToolchainSystemIncludes;

  public CToolchainIdeInfo(
      ImmutableList<String> baseCompilerOptions,
      ImmutableList<String> cCompilerOptions,
      ImmutableList<String> cppCompilerOptions,
      ImmutableList<ExecutionRootPath> builtInIncludeDirectories,
      ExecutionRootPath cppExecutable,
      String targetName,
      ImmutableList<String> unfilteredCompilerOptions,
      ImmutableList<ExecutionRootPath> unfilteredToolchainSystemIncludes) {
    this.baseCompilerOptions = baseCompilerOptions;
    this.cCompilerOptions = cCompilerOptions;
    this.cppCompilerOptions = cppCompilerOptions;
    this.builtInIncludeDirectories = builtInIncludeDirectories;
    this.cppExecutable = cppExecutable;
    this.targetName = targetName;
    this.unfilteredCompilerOptions = unfilteredCompilerOptions;
    this.unfilteredToolchainSystemIncludes = unfilteredToolchainSystemIncludes;
  }

  public ImmutableList<String> getBaseCompilerOptions() {
    return baseCompilerOptions;
  }

  public ImmutableList<String> getcCompilerOptions() {
    return cCompilerOptions;
  }

  public ImmutableList<String> getCppCompilerOptions() {
    return cppCompilerOptions;
  }

  public ImmutableList<ExecutionRootPath> getBuiltInIncludeDirectories() {
    return builtInIncludeDirectories;
  }

  public ExecutionRootPath getCppExecutable() {
    return cppExecutable;
  }

  public String getTargetName() {
    return targetName;
  }

  public ImmutableList<String> getUnfilteredCompilerOptions() {
    return unfilteredCompilerOptions;
  }

  public ImmutableList<ExecutionRootPath> getUnfilteredToolchainSystemIncludes() {
    return unfilteredToolchainSystemIncludes;
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Builder for c toolchain */
  public static class Builder {
    private final ImmutableList.Builder<String> baseCompilerOptions = ImmutableList.builder();
    private final ImmutableList.Builder<String> cCompilerOptions = ImmutableList.builder();
    private final ImmutableList.Builder<String> cppCompilerOptions = ImmutableList.builder();

    private final ImmutableList.Builder<ExecutionRootPath> builtInIncludeDirectories =
        ImmutableList.builder();

    ExecutionRootPath cppExecutable;

    String targetName = "";

    private final ImmutableList.Builder<String> unfilteredCompilerOptions = ImmutableList.builder();
    private final ImmutableList.Builder<ExecutionRootPath> unfilteredToolchainSystemIncludes =
        ImmutableList.builder();

    public Builder addBaseCompilerOptions(Iterable<String> baseCompilerOptions) {
      this.baseCompilerOptions.addAll(baseCompilerOptions);
      return this;
    }

    public Builder addCCompilerOptions(Iterable<String> cCompilerOptions) {
      this.cCompilerOptions.addAll(cCompilerOptions);
      return this;
    }

    public Builder addCppCompilerOptions(Iterable<String> cppCompilerOptions) {
      this.cppCompilerOptions.addAll(cppCompilerOptions);
      return this;
    }

    public Builder addBuiltInIncludeDirectories(
        Iterable<ExecutionRootPath> builtInIncludeDirectories) {
      this.builtInIncludeDirectories.addAll(builtInIncludeDirectories);
      return this;
    }

    public Builder setCppExecutable(ExecutionRootPath cppExecutable) {
      this.cppExecutable = cppExecutable;
      return this;
    }

    public Builder setTargetName(String targetName) {
      this.targetName = targetName;
      return this;
    }

    public Builder addUnfilteredCompilerOptions(Iterable<String> unfilteredCompilerOptions) {
      this.unfilteredCompilerOptions.addAll(unfilteredCompilerOptions);
      return this;
    }

    public Builder addUnfilteredToolchainSystemIncludes(
        Iterable<ExecutionRootPath> unfilteredToolchainSystemIncludes) {
      this.unfilteredToolchainSystemIncludes.addAll(unfilteredToolchainSystemIncludes);
      return this;
    }

    public CToolchainIdeInfo build() {
      return new CToolchainIdeInfo(
          baseCompilerOptions.build(),
          cCompilerOptions.build(),
          cppCompilerOptions.build(),
          builtInIncludeDirectories.build(),
          cppExecutable,
          targetName,
          unfilteredCompilerOptions.build(),
          unfilteredToolchainSystemIncludes.build());
    }
  }

  @Override
  public String toString() {
    return "CToolchainIdeInfo{"
        + "\n"
        + "  baseCompilerOptions="
        + getBaseCompilerOptions()
        + "\n"
        + "  cCompilerOptions="
        + getcCompilerOptions()
        + "\n"
        + "  cppCompilerOptions="
        + getCppCompilerOptions()
        + "\n"
        + "  builtInIncludeDirectories="
        + getBuiltInIncludeDirectories()
        + "\n"
        + "  cppExecutable='"
        + getCppExecutable()
        + '\''
        + "\n"
        + "  targetName='"
        + getTargetName()
        + '\''
        + "\n"
        + "  unfilteredCompilerOptions="
        + getUnfilteredCompilerOptions()
        + "\n"
        + "  unfilteredToolchainSystemIncludes="
        + getUnfilteredToolchainSystemIncludes()
        + "\n"
        + '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    CToolchainIdeInfo that = (CToolchainIdeInfo) o;
    return Objects.equal(getBaseCompilerOptions(), that.getBaseCompilerOptions())
        && Objects.equal(getcCompilerOptions(), that.getcCompilerOptions())
        && Objects.equal(getCppCompilerOptions(), that.getCppCompilerOptions())
        && Objects.equal(getBuiltInIncludeDirectories(), that.getBuiltInIncludeDirectories())
        && Objects.equal(getCppExecutable(), that.getCppExecutable())
        && Objects.equal(getTargetName(), that.getTargetName())
        && Objects.equal(getUnfilteredCompilerOptions(), that.getUnfilteredCompilerOptions())
        && Objects.equal(
            getUnfilteredToolchainSystemIncludes(), that.getUnfilteredToolchainSystemIncludes());
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(
        getBaseCompilerOptions(),
        getcCompilerOptions(),
        getCppCompilerOptions(),
        getBuiltInIncludeDirectories(),
        getCppExecutable(),
        getTargetName(),
        getUnfilteredCompilerOptions(),
        getUnfilteredToolchainSystemIncludes());
  }
}
