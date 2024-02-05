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
package com.google.idea.blaze.java.fastbuild;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.model.BlazeVersionData;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.settings.BuildSystemName;

final class BazelFastBuildDeployJarStrategy extends FastBuildDeployJarStrategy {

  @Override
  public ImmutableSet<BuildSystemName> getSupportedBuildSystems() {
    return ImmutableSet.of(BuildSystemName.Bazel);
  }

  @Override
  public ImmutableList<? extends TargetExpression> getBuildTargets(
      Label label, BlazeVersionData versionData) {
    if (versionData.bazelIsAtLeastVersion(7, 0, 1)) {
      return ImmutableList.of(label);
    }
    return ImmutableList.of(createDeployJarLabel(label, versionData), label);
  }

  @Override
  public ImmutableList<String> getBuildFlags(BlazeVersionData versionData) {
    if (versionData.bazelIsAtLeastVersion(7, 0, 1)) {
      return ImmutableList.of(
          "--experimental_java_test_auto_create_deploy_jar",
          "--output_groups=+_hidden_top_level_INTERNAL_");
    }
    return ImmutableList.of();
  }

  @Override
  public Label createDeployJarLabel(Label label, BlazeVersionData versionData) {
    if (versionData.bazelIsAtLeastVersion(7, 0, 1)) {
      return Label.create(label + "_auto_deploy.jar");
    }
    return Label.create(label + "_deploy.jar");
  }

  @Override
  public Label deployJarOwnerLabel(Label label, BlazeVersionData versionData) {
    if (versionData.bazelIsAtLeastVersion(7, 0, 1)) {
      return label;
    }
    return createDeployJarLabel(label, versionData);
  }
}
