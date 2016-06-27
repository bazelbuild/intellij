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

import java.io.Serializable;

/**
 * Represents the java_toolchain class
 */
public class JavaToolchainIdeInfo implements Serializable {
  private static final long serialVersionUID = 1L;

  public final String sourceVersion;
  public final String targetVersion;

  public JavaToolchainIdeInfo(String sourceVersion, String targetVersion) {
    this.sourceVersion = sourceVersion;
    this.targetVersion = targetVersion;
  }

  @Override
  public String toString() {
    return "JavaToolchainIdeInfo{" + "\n" +
           "  sourceVersion=" + sourceVersion + "\n" +
           "  targetVersion=" + targetVersion + "\n" +
           '}';
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    String sourceVersion;
    String targetVersion;

    public Builder setSourceVersion(String sourceVersion) {
      this.sourceVersion = sourceVersion;
      return this;
    }

    public Builder setTargetVersion(String targetVersion) {
      this.targetVersion = targetVersion;
      return this;
    }

    public JavaToolchainIdeInfo build() {
      return new JavaToolchainIdeInfo(sourceVersion, targetVersion);
    }
  }
}
