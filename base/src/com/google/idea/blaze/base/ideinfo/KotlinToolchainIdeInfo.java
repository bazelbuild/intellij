/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.model.primitives.Label;
import java.io.Serializable;

/** Kotlin toolchain information. */
public class KotlinToolchainIdeInfo implements Serializable {
  private static final long serialVersionUID = 1L;

  public final String languageVersion;
  public final ImmutableList<Label> sdkTargets;

  public KotlinToolchainIdeInfo(String languageVersion, ImmutableList<Label> sdkTargets) {
    this.languageVersion = languageVersion;
    this.sdkTargets = sdkTargets;
  }

  @Override
  public String toString() {
    return "KotlinToolchainIdeInfo{"
        + "\n"
        + "  languageVersion="
        + languageVersion
        + "\n"
        + "  sdkTargets="
        + sdkTargets
        + "\n"
        + '}';
  }

  public static KotlinToolchainIdeInfo.Builder builder() {
    return new KotlinToolchainIdeInfo.Builder();
  }

  /** Builder for kotlin toolchain info */
  public static class Builder {
    String languageVersion;
    ImmutableList<Label> sdkTargets;

    public KotlinToolchainIdeInfo.Builder setLanguageVersion(String languageVersion) {
      this.languageVersion = languageVersion;
      return this;
    }

    public KotlinToolchainIdeInfo.Builder setSdkTargets(ImmutableList<Label> sdkTargets) {
      this.sdkTargets = sdkTargets;
      return this;
    }

    public KotlinToolchainIdeInfo build() {
      return new KotlinToolchainIdeInfo(languageVersion, sdkTargets);
    }
  }
}
