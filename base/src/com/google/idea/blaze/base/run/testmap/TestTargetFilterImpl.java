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
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.run.TestTargetFinder;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.sync.SyncListener;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/**
 * Used to locate tests from source files for things like right-clicks.
 *
 * <p>It's essentially a map from source file -> reachable test rules.
 */
public class TestTargetFilterImpl implements TestTargetFinder {

  private final Project project;
  @Nullable private TestMap testMap;

  static class TestMap {
    private final Project project;
    private final Multimap<File, TargetKey> rootsMap;
    private final TargetMap targetMap;

    TestMap(Project project, ArtifactLocationDecoder artifactLocationDecoder, TargetMap targetMap) {
      this.project = project;
      this.rootsMap = createRootsMap(artifactLocationDecoder, targetMap.targets());
      this.targetMap = targetMap;
    }

    private Collection<TargetIdeInfo> testTargetsForSourceFile(File sourceFile) {
      BlazeProjectData blazeProjectData =
          BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
      if (blazeProjectData != null) {
        return testTargetsForSourceFileImpl(blazeProjectData.reverseDependencies, sourceFile);
      }
      return ImmutableList.of();
    }

    @VisibleForTesting
    Collection<Label> testTargetsForSourceFile(
        ImmutableMultimap<TargetKey, TargetKey> rdepsMap, File sourceFile) {
      return testTargetsForSourceFileImpl(rdepsMap, sourceFile)
          .stream()
          .filter(TargetIdeInfo::isPlainTarget)
          .map(target -> target.key.label)
          .collect(Collectors.toList());
    }

    Collection<TargetIdeInfo> testTargetsForSourceFileImpl(
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
        if (isTestTarget(target)) {
          result.add(target);
        }
        for (TargetKey rdep : rdepsMap.get(targetKey)) {
          todo.add(rdep);
        }
      }
      return result;
    }

    static Multimap<File, TargetKey> createRootsMap(
        ArtifactLocationDecoder artifactLocationDecoder, Collection<TargetIdeInfo> targets) {
      Multimap<File, TargetKey> result = ArrayListMultimap.create();
      for (TargetIdeInfo target : targets) {
        for (ArtifactLocation source : target.sources) {
          result.put(artifactLocationDecoder.decode(source), target.key);
        }
      }
      return result;
    }

    private static boolean isTestTarget(@Nullable TargetIdeInfo target) {
      return target != null
          && target.kind != null
          && target.kind.isOneOf(
              Kind.ANDROID_ROBOLECTRIC_TEST,
              Kind.ANDROID_TEST,
              Kind.JAVA_TEST,
              Kind.GWT_TEST,
              Kind.CC_TEST,
              Kind.PY_TEST);
    }
  }

  public TestTargetFilterImpl(Project project) {
    this.project = project;
  }

  @Override
  public Collection<TargetIdeInfo> testTargetsForSourceFile(File sourceFile) {
    TestMap testMap = getTestMap();
    if (testMap == null) {
      return ImmutableList.of();
    }
    return testMap.testTargetsForSourceFile(sourceFile);
  }

  private synchronized TestMap getTestMap() {
    if (testMap == null) {
      testMap = initTestMap();
    }
    return testMap;
  }

  @Nullable
  private TestMap initTestMap() {
    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (blazeProjectData == null) {
      return null;
    }
    return new TestMap(
        project, blazeProjectData.artifactLocationDecoder, blazeProjectData.targetMap);
  }

  private synchronized void clearMapData() {
    this.testMap = null;
  }

  static class ClearTestMap extends SyncListener.Adapter {
    @Override
    public void onSyncComplete(
        Project project,
        BlazeContext context,
        BlazeImportSettings importSettings,
        ProjectViewSet projectViewSet,
        BlazeProjectData blazeProjectData,
        SyncResult syncResult) {
      TestTargetFinder testTargetFinder = TestTargetFinder.getInstance(project);
      ((TestTargetFilterImpl) testTargetFinder).clearMapData();
    }
  }
}
