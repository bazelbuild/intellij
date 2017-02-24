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

import com.google.idea.blaze.base.model.BlazeVersionData;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.openapi.project.Project;

class AspectStrategyProviderBazel implements AspectStrategyProvider {
  private static final BoolExperiment useSkylarkAspect =
      new BoolExperiment("use.skylark.aspect.bazel.3", true);

  @Override
  public AspectStrategy getAspectStrategy(Project project, BlazeVersionData blazeVersionData) {
    boolean canUseSkylark =
        useSkylarkAspect.getValue() && blazeVersionData.bazelIsAtLeastVersion(0, 4, 5);

    return canUseSkylark ? new AspectStrategySkylark() : new AspectStrategyNative();
  }
}
