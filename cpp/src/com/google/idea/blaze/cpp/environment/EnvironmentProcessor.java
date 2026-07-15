/*
 * Copyright 2025 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.cpp.environment;

import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.base.sync.workspace.ExecutionRootPathResolver;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.util.ObjectUtils;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * Post-processes the environment variables of a cc toolchain before they are handed to the IDE.
 *
 * <p>Analogous to {@link com.google.idea.blaze.cpp.copts.CoptsProcessor} but for the toolchain
 * environment map instead of the copts list.
 */
public interface EnvironmentProcessor {

  ExtensionPointName<EnvironmentProcessor> EP_NAME = new ExtensionPointName<>("com.google.idea.blaze.cpp.environment.EnvironmentProcessor");

  /**
   * Specialized for transforming individual environment values.
   */
  abstract class Transform implements EnvironmentProcessor {

    /**
     * Transform the value of a single environment variable. Returning {@code null} leaves the value
     * unchanged.
     */
    @Nullable
    protected abstract String apply(String key, String value, ExecutionRootPathResolver resolver);

    @Override
    public final void process(Map<String, String> environment, ExecutionRootPathResolver resolver) {
      environment.replaceAll((key, value) -> ObjectUtils.coalesce(apply(key, value, resolver), value));
    }
  }

  /**
   * Whether the processor is enabled. Normally just a registry key check.
   */
  boolean enabled();

  /**
   * Processes the environment map inplace.
   */
  void process(Map<String, String> environment, ExecutionRootPathResolver resolver);

  static ImmutableMap<String, String> apply(
      ImmutableMap<String, String> environment,
      ExecutionRootPathResolver resolver
  ) {
    final var mutable = new LinkedHashMap<>(environment);
    for (final var processor : EP_NAME.getExtensionList()) {
      if (processor.enabled()) {
        processor.process(mutable, resolver);
      }
    }

    return ImmutableMap.copyOf(mutable);
  }
}
