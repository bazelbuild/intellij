/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.run.testmap;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Queues;
import com.google.common.collect.Sets;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** Filters a {@link TargetMap} according to a given filter. */
public class FilteredTargetMap {

  private final Project project;
  private final Multimap<File, TargetKey> rootsMap;
  private final TargetMap targetMap;
  private final Predicate<TargetIdeInfo> filter;

  public FilteredTargetMap(
      Project project,
      ArtifactLocationDecoder artifactLocationDecoder,
      TargetMap targetMap,
      Predicate<TargetIdeInfo> filter) {
    this.project = project;
    this.rootsMap = createRootsMap(artifactLocationDecoder, targetMap.targets());
    this.targetMap = targetMap;
    this.filter = filter;
  }

  public Collection<TargetIdeInfo> targetsForSourceFile(File sourceFile) {
    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (blazeProjectData != null) {
      return targetsForSourceFileImpl(blazeProjectData.reverseDependencies, sourceFile);
    }
    return ImmutableList.of();
  }

  @VisibleForTesting
  Collection<Label> targetsForSourceFile(
      ImmutableMultimap<TargetKey, TargetKey> rdepsMap, File sourceFile) {
    return targetsForSourceFileImpl(rdepsMap, sourceFile)
        .stream()
        .filter(TargetIdeInfo::isPlainTarget)
        .map(target -> target.key.label)
        .collect(Collectors.toList());
  }

  private Collection<TargetIdeInfo> targetsForSourceFileImpl(
      ImmutableMultimap<TargetKey, TargetKey> rdepsMap, File sourceFile) {
    List<TargetIdeInfo> result = Lists.newArrayList();
    Collection<TargetKey> roots = rootsMap.get(sourceFile);

    Queue<TargetKey> todo = Queues.newArrayDeque();
    for (TargetKey label : roots) {
      todo.add(label);
    }
    Set<TargetKey> seen = Sets.newHashSet();
    while (!todo.isEmpty()) {
      TargetKey targetKey = todo.remove();
      if (!seen.add(targetKey)) {
        continue;
      }

      TargetIdeInfo target = targetMap.get(targetKey);
      if (filter.test(target)) {
        result.add(target);
      }
      for (TargetKey rdep : rdepsMap.get(targetKey)) {
        todo.add(rdep);
      }
    }
    return result;
  }

  private static Multimap<File, TargetKey> createRootsMap(
      ArtifactLocationDecoder artifactLocationDecoder, Collection<TargetIdeInfo> targets) {
    Multimap<File, TargetKey> result = ArrayListMultimap.create();
    for (TargetIdeInfo target : targets) {
      for (ArtifactLocation source : target.sources) {
        result.put(artifactLocationDecoder.decode(source), target.key);
      }
    }
    return result;
  }
}
