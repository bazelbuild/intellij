/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.targetmaps;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Queues;
import com.google.common.collect.Sets;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import java.util.Collection;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

/** Handy class to find all transitive dependencies of a given target */
public class TransitiveDependencyMap {
  private final Project project;

  public static TransitiveDependencyMap getInstance(Project project) {
    return ServiceManager.getService(project, TransitiveDependencyMap.class);
  }

  public TransitiveDependencyMap(Project project) {
    this.project = project;
  }

  public ImmutableCollection<TargetKey> getTransitiveDependencies(TargetKey targetKey) {
    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (blazeProjectData == null) {
      return ImmutableSet.of();
    }
    // TODO: see if we need caching.
    return getTransitiveDependencies(targetKey, blazeProjectData.targetMap);
  }

  public static ImmutableCollection<TargetKey> getTransitiveDependencies(
      TargetKey targetKey, TargetMap targetMap) {
    return getTransitiveDependencies(ImmutableList.of(targetKey), targetMap);
  }

  public static ImmutableCollection<TargetKey> getTransitiveDependencies(
      Collection<TargetKey> targetKeys, TargetMap targetMap) {
    Queue<TargetKey> targetsToVisit = Queues.newArrayDeque();
    Set<TargetKey> transitiveDependencies = Sets.newHashSet();
    targetsToVisit.addAll(targetKeys);
    while (!targetsToVisit.isEmpty()) {
      TargetIdeInfo currentTarget = targetMap.get(targetsToVisit.remove());
      if (currentTarget == null) {
        continue;
      }
      List<TargetKey> newDependencies =
          currentTarget
              .dependencies
              .stream()
              .map(d -> TargetKey.forPlainTarget(d.targetKey.label))
              // Get rid of the ones we've already seen.
              .filter(r -> !transitiveDependencies.contains(r))
              .collect(Collectors.toList());
      targetsToVisit.addAll(newDependencies);
      transitiveDependencies.addAll(newDependencies);
    }
    return ImmutableSet.copyOf(transitiveDependencies);
  }
}
