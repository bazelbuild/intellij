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
package com.google.idea.blaze.kotlin.sync.model;

import javax.annotation.Nonnull;
import java.io.Serializable;
import java.util.Objects;

public final class BlazeKotlinToolchainIdeInfo implements Serializable {
  public static final long serialVersionUID = 2L;

  @Nonnull public final String label;
  @Nonnull public final CommonInfo common;
  @Nonnull public final JvmInfo jvm;

  BlazeKotlinToolchainIdeInfo(
      @Nonnull String label, @Nonnull CommonInfo common, @Nonnull JvmInfo jvm) {
    this.label = Objects.requireNonNull(label);
    this.common = Objects.requireNonNull(common);
    this.jvm = Objects.requireNonNull(jvm);
  }

  public static final class CommonInfo implements Serializable {
    public static final long serialVersionUID = 2L;

    @Nonnull public final String languageVersion;
    @Nonnull public final String apiVersion;
    @Nonnull public final String coroutines;

    CommonInfo(
        @Nonnull String languageVersion, @Nonnull String apiVersion, @Nonnull String coroutines) {
      this.languageVersion = Objects.requireNonNull(languageVersion);
      this.apiVersion = Objects.requireNonNull(apiVersion);
      this.coroutines = Objects.requireNonNull(coroutines);
    }
  }

  public static final class JvmInfo implements Serializable {
    public static final long serialVersionUID = 2L;

    @Nonnull public final String jvmTarget;

    JvmInfo(@Nonnull String jvmTarget) {
      this.jvmTarget = Objects.requireNonNull(jvmTarget);
    }
  }
}
