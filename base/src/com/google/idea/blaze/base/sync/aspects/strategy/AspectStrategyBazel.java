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
package com.google.idea.blaze.base.sync.aspects.strategy;

import com.google.common.annotations.VisibleForTesting;
import com.google.idea.blaze.base.model.BlazeVersionData;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.google.idea.blaze.base.sync.aspects.storage.AspectRepositoryProvider;
import com.google.idea.blaze.base.sync.aspects.storage.AspectStorageService;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.project.Project;

import java.io.File;
import java.util.Optional;
import javax.annotation.Nullable;

/** Aspect strategy for Bazel, where the aspect is situated in an external repository. */
public class AspectStrategyBazel extends AspectStrategy {
  Boolean supportsAspectParameters;

  static final class Provider implements AspectStrategyProvider {
    @Override
    @Nullable
    public AspectStrategy getStrategy(BlazeVersionData versionData) {
      return versionData.buildSystem() == BuildSystemName.Bazel
          ? new AspectStrategyBazel(versionData)
          : null;
    }
  }

  @VisibleForTesting
  public AspectStrategyBazel(BlazeVersionData versionData) {
    super(/* aspectSupportsDirectDepsTrimming= */ true);
    supportsAspectParameters = versionData.bazelIsAtLeastVersion(6, 0, 0);
  }

  @Override
  @VisibleForTesting
  public Optional<String> getAspectFlag(Project project) {
    return AspectStorageService.of(project).resolve("intellij_info_bundled.bzl")
        .map(label -> String.format("--aspects=%s%%intellij_info_aspect", label));
  }

  @Override
  protected Boolean supportsAspectsParameters() {
    return supportsAspectParameters;
  }

  @Override
  public String getName() {
    return "AspectStrategySkylarkBazel";
  }
}
