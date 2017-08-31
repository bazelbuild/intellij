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

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.model.BlazeVersionData;
import com.google.idea.blaze.base.settings.Blaze.BuildSystem;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import java.io.File;
import java.util.List;

class AspectStrategyProviderBazel implements AspectStrategyProvider {
  @Override
  public AspectStrategy getAspectStrategy(BlazeVersionData blazeVersionData) {
    if (blazeVersionData.buildSystem() != BuildSystem.Bazel) {
      return null;
    }
    return new AspectStrategyBazel(blazeVersionData);
  }

  private static class AspectStrategyBazel extends AspectStrategy {

    private final BlazeVersionData blazeVersionData;

    private AspectStrategyBazel(BlazeVersionData blazeVersionData) {
      this.blazeVersionData = blazeVersionData;
    }

    @Override
    public String getName() {
      return "AspectStrategySkylarkBazel";
    }

    @Override
    protected boolean hasPerLanguageOutputGroups() {
      return useBundledAspect();
    }

    @Override
    protected List<String> getAspectFlags() {
      if (useBundledAspect()) {
        return ImmutableList.of(
            "--aspects=@intellij_aspect//:intellij_info.bzl%intellij_info_aspect",
            getAspectRepositoryOverrideFlag());
      }
      return ImmutableList.of(
          "--aspects=@bazel_tools//tools/ide:intellij_info.bzl%intellij_info_aspect");
    }

    private boolean useBundledAspect() {
      return blazeVersionData.bazelIsAtLeastVersion(0, 5, 0);
    }

    private static File findAspectDirectory() {
      IdeaPluginDescriptor plugin =
          PluginManager.getPlugin(
              PluginManager.getPluginByClassName(AspectStrategy.class.getName()));
      return new File(plugin.getPath(), "aspect");
    }

    private static String getAspectRepositoryOverrideFlag() {
      return String.format(
          "--override_repository=intellij_aspect=%s", findAspectDirectory().getPath());
    }
  }
}
