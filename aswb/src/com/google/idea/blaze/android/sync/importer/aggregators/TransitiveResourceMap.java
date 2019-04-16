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
import com.google.idea.blaze.base.ideinfo.AndroidResFolder;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

/** Computes transitive resources. */
public class TransitiveResourceMap
    extends TargetIdeInfoTransitiveAggregator<TransitiveResourceInfo> {
  /** The transitive info computed per-rule */
  public static class TransitiveResourceInfo {
    public static final TransitiveResourceInfo NO_RESOURCES = new TransitiveResourceInfo();
    public final Set<AndroidResFolder> transitiveResources = Sets.newHashSet();
    public final Set<TargetKey> transitiveResourceTargets = Sets.newHashSet();
  }

  public TransitiveResourceMap(TargetMap targetMap) {
    super(targetMap);
  }

  public ArtifactLocation getManifestFile(ArtifactLocation resource) {
    TargetIdeInfo targetIdeInfo = transitiveResourcesToTargetIdeInfo.get(resource);
    return targetIdeInfo == null ? null : targetIdeInfo.getAndroidIdeInfo().getManifest();
  }

  @Override
  protected Iterable<TargetKey> getDependencies(TargetIdeInfo target) {
    return getResourceDependencies(target);
  }

  @NotNull
  public static List<TargetKey> getResourceDependencies(TargetIdeInfo target) {
    List<TargetKey> regularDependencies =
        TargetIdeInfoTransitiveAggregator.getCompileDependencies(target);

    AndroidIdeInfo androidIdeInfo = target.getAndroidIdeInfo();
    if (androidIdeInfo != null && androidIdeInfo.getLegacyResources() != null) {
      List<TargetKey> result = Lists.newArrayList(regularDependencies);
      result.add(TargetKey.forPlainTarget(androidIdeInfo.getLegacyResources()));
      return result;
    }
    return regularDependencies;
  }

  public TransitiveResourceInfo get(TargetKey targetKey) {
    return getOrDefault(targetKey, TransitiveResourceInfo.NO_RESOURCES);
  }

  @Override
  protected TransitiveResourceInfo createForTarget(TargetIdeInfo target) {
    TransitiveResourceInfo result = new TransitiveResourceInfo();
    AndroidIdeInfo androidIdeInfo = target.getAndroidIdeInfo();
    if (target.getAndroidAarIdeInfo() != null) {
      result.transitiveResourceTargets.add(target.getKey());
    }
    if (androidIdeInfo != null && androidIdeInfo.getLegacyResources() == null) {
      for (AndroidResFolder resource : androidIdeInfo.getResFolders()) {
        // For each target, it's hard to tell whether a manifest file is specific for a resource
        // since targets are allowed have same resource directory but different manifest files.
        // So for a target, we have the following assumption
        // 1. A target's manifest file may be specific for its resource when its resource folder and
        // its BUILD file are under same directory
        // 2. If multiple targets meet requirement 1, the closest to resource folder wins
        if (target.getBuildFile() != null && androidIdeInfo.getManifest() != null) {
          String buildFile = target.getBuildFile().getRelativePath();
          if (buildFile == null) {
            continue;
          }
          String buildFileParent = buildFile.split("/BUILD")[0];
          TargetIdeInfo targetIdeInfo =
              this.transitiveResourcesToTargetIdeInfo.get(resource.getRoot());
          if (resource.getRoot().getRelativePath().startsWith(buildFileParent)
              && (targetIdeInfo == null
                  || targetIdeInfo.getBuildFile().getRelativePath().length()
                      < buildFile.length())) {
            this.transitiveResourcesToTargetIdeInfo.put(resource.getRoot(), target);
          }
        }
        result.transitiveResources.add(resource);
      }
      result.transitiveResourceTargets.add(target.getKey());
    }
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
