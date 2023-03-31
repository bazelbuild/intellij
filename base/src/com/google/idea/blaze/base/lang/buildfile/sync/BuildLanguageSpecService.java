/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.lang.buildfile.sync;

import com.google.idea.blaze.base.lang.buildfile.language.semantics.BuildLanguageSpec;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import java.time.Duration;
import java.time.Instant;
import javax.annotation.Nullable;

/** Project-level service for retrieving build language spec output from `info build-language` */
@Service
public final class BuildLanguageSpecService {
  private static final Duration REFRESH_CADENCE = Duration.ofDays(1);

  private BuildLanguageSpec languageSpec = null;
  private Instant timestamp = Instant.now();

  public BuildLanguageSpecService(Project project) {}

  @Nullable
  public BuildLanguageSpec getLanguageSpec() {
    return languageSpec;
  }

  public void setLanguageSpec(BuildLanguageSpec languageSpec) {
    this.languageSpec = languageSpec;
    timestamp = Instant.now();
  }

  public boolean shouldFetchLanguageSpec() {
    return languageSpec == null || timestamp.plus(REFRESH_CADENCE).isBefore(Instant.now());
  }
}
