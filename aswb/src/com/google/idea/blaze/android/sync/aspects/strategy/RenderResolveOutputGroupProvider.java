/*
 * Copyright 2020 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.sync.aspects.strategy;

import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.android.projectsystem.RenderJarClassFileFinder;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.sync.aspects.strategy.AspectStrategy.OutputGroup;
import com.google.idea.blaze.base.sync.aspects.strategy.OutputGroupsProvider;

/** Adds output group required by {@link RenderJarClassFileFinder} when it is enabled. */
public class RenderResolveOutputGroupProvider implements OutputGroupsProvider {
  private static final ImmutableSet<String> RESOLVE_OUTPUT_GROUP =
      ImmutableSet.of("intellij-render-resolve-android");

  @Override
  public ImmutableSet<String> getAdditionalOutputGroups(
      OutputGroup outputGroup, ImmutableSet<LanguageClass> activeLanguages) {
    if (!(outputGroup.equals(OutputGroup.RESOLVE) || outputGroup.equals(OutputGroup.COMPILE))
        || !activeLanguages.contains(LanguageClass.ANDROID)
        || !RenderJarClassFileFinder.isEnabled()) {
      return ImmutableSet.of();
    }

    // we want Render Jars to be built for syncs (OutputGroup.RESOLVE) and builds
    // (OutputGroup.COMPILE) for android projects whenever RenderJarClassFileFinder is enabled
    return RESOLVE_OUTPUT_GROUP;
  }
}
