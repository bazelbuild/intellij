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
package com.google.idea.blaze.android.sync.importer.aggregators;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.idea.blaze.android.sync.importer.aggregators.TransitiveResourceMap.TransitiveResourceInfo;
import com.google.idea.blaze.base.ideinfo.AndroidIdeInfo;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import java.util.List;
import java.util.Set;

/** Computes transitive resources. */
public class TransitiveResourceMap
    extends TargetIdeInfoTransitiveAggregator<TransitiveResourceInfo> {
  /** The transitive info computed per-rule */
  public static class TransitiveResourceInfo {
    public static final TransitiveResourceInfo NO_RESOURCES = new TransitiveResourceInfo();
    public final Set<ArtifactLocation> transitiveResources = Sets.newHashSet();
    public final Set<TargetKey> transitiveResourceTargets = Sets.newHashSet();
  }

  public TransitiveResourceMap(TargetMap targetMap) {
    super(targetMap);
  }

  @Override
  protected Iterable<TargetKey> getDependencies(TargetIdeInfo target) {
    AndroidIdeInfo androidIdeInfo = target.getAndroidIdeInfo();
    if (androidIdeInfo != null && androidIdeInfo.getLegacyResources() != null) {
      List<TargetKey> result = Lists.newArrayList(super.getDependencies(target));
      result.add(TargetKey.forPlainTarget(androidIdeInfo.getLegacyResources()));
      return result;
    }
    return super.getDependencies(target);
  }

  public TransitiveResourceInfo get(TargetKey targetKey) {
    return getOrDefault(targetKey, TransitiveResourceInfo.NO_RESOURCES);
  }

  @Override
  protected TransitiveResourceInfo createForTarget(TargetIdeInfo target) {
    TransitiveResourceInfo result = new TransitiveResourceInfo();
    AndroidIdeInfo androidIdeInfo = target.getAndroidIdeInfo();
    if (androidIdeInfo == null) {
      return result;
    }
    if (androidIdeInfo.getLegacyResources() != null) {
      return result;
    }
    result.transitiveResources.addAll(androidIdeInfo.getResources());
    result.transitiveResourceTargets.add(target.getKey());
    return result;
  }

  @Override
  protected TransitiveResourceInfo reduce(
      TransitiveResourceInfo value, TransitiveResourceInfo dependencyValue) {
    value.transitiveResources.addAll(dependencyValue.transitiveResources);
    value.transitiveResourceTargets.addAll(dependencyValue.transitiveResourceTargets);
    return value;
  }
}
