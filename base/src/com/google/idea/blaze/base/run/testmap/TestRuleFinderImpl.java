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
import com.google.idea.blaze.base.ideinfo.RuleIdeInfo;
import com.google.idea.blaze.base.ideinfo.RuleKey;
import com.google.idea.blaze.base.ideinfo.RuleMap;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.run.TestRuleFinder;
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
public class TestRuleFinderImpl implements TestRuleFinder {

  private final Project project;
  @Nullable private TestMap testMap;

  static class TestMap {
    private final Project project;
    private final Multimap<File, RuleKey> rootsMap;
    private final RuleMap ruleMap;

    TestMap(Project project, ArtifactLocationDecoder artifactLocationDecoder, RuleMap ruleMap) {
      this.project = project;
      this.rootsMap = createRootsMap(artifactLocationDecoder, ruleMap.rules());
      this.ruleMap = ruleMap;
    }

    private Collection<RuleIdeInfo> testTargetsForSourceFile(File sourceFile) {
      BlazeProjectData blazeProjectData =
          BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
      if (blazeProjectData != null) {
        return testRulesForSourceFile(blazeProjectData.reverseDependencies, sourceFile);
      }
      return ImmutableList.of();
    }

    @VisibleForTesting
    Collection<Label> testTargetsForSourceFile(
        ImmutableMultimap<RuleKey, RuleKey> rdepsMap, File sourceFile) {
      return testRulesForSourceFile(rdepsMap, sourceFile)
          .stream()
          .filter(RuleIdeInfo::isPlainTarget)
          .map(rule -> rule.label)
          .collect(Collectors.toList());
    }

    Collection<RuleIdeInfo> testRulesForSourceFile(
        ImmutableMultimap<RuleKey, RuleKey> rdepsMap, File sourceFile) {
      List<RuleIdeInfo> result = Lists.newArrayList();
      Collection<RuleKey> roots = rootsMap.get(sourceFile);

      Queue<RuleKey> todo = Queues.newArrayDeque();
      for (RuleKey label : roots) {
        todo.add(label);
      }
      Set<RuleKey> seen = Sets.newHashSet();
      while (!todo.isEmpty()) {
        RuleKey ruleKey = todo.remove();
        if (!seen.add(ruleKey)) {
          continue;
        }

        RuleIdeInfo rule = ruleMap.get(ruleKey);
        if (isTestRule(rule)) {
          result.add(rule);
        }
        for (RuleKey rdep : rdepsMap.get(ruleKey)) {
          todo.add(rdep);
        }
      }
      return result;
    }

    static Multimap<File, RuleKey> createRootsMap(
        ArtifactLocationDecoder artifactLocationDecoder, Collection<RuleIdeInfo> rules) {
      Multimap<File, RuleKey> result = ArrayListMultimap.create();
      for (RuleIdeInfo ruleIdeInfo : rules) {
        for (ArtifactLocation source : ruleIdeInfo.sources) {
          result.put(artifactLocationDecoder.decode(source), ruleIdeInfo.key);
        }
      }
      return result;
    }

    private static boolean isTestRule(@Nullable RuleIdeInfo rule) {
      return rule != null
          && rule.kind != null
          && rule.kind.isOneOf(
              Kind.ANDROID_ROBOLECTRIC_TEST,
              Kind.ANDROID_TEST,
              Kind.JAVA_TEST,
              Kind.GWT_TEST,
              Kind.CC_TEST);
    }
  }

  public TestRuleFinderImpl(Project project) {
    this.project = project;
  }

  @Override
  public Collection<RuleIdeInfo> testTargetsForSourceFile(File sourceFile) {
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
    return new TestMap(project, blazeProjectData.artifactLocationDecoder, blazeProjectData.ruleMap);
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
      TestRuleFinder testRuleFinder = TestRuleFinder.getInstance(project);
      ((TestRuleFinderImpl) testRuleFinder).clearMapData();
    }
  }
}
