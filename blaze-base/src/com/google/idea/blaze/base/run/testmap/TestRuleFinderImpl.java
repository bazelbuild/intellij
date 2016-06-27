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
import com.google.common.collect.*;
import com.google.idea.blaze.base.experiments.BoolExperiment;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.RuleIdeInfo;
import com.google.idea.blaze.base.ideinfo.TestIdeInfo;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.run.TestRuleFinder;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.sync.SyncListener;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.intellij.openapi.project.Project;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Queue;
import java.util.Set;

/**
 * Used to locate tests from source files for things like right-clicks.
 *
 * It's essentially a map from source file -> reachable test rules.
 */
public class TestRuleFinderImpl implements TestRuleFinder {

  // Safety experiment to allow us to turn this off. Deployed in ijwb 1.2.
  private static final BoolExperiment USE_TEST_SIZE = new BoolExperiment("use.test.sizes", true);

  private final Project project;
  @Nullable
  private TestMap testMap;

  static class TestMap {
    private final Project project;
    private final Multimap<File, Label> rootsMap;
    private final ImmutableMap<Label, RuleIdeInfo> ruleMap;

    TestMap(Project project, ImmutableMap<Label, RuleIdeInfo> ruleMap) {
      this.project = project;
      this.rootsMap = createRootsMap(ruleMap.values());
      this.ruleMap = ruleMap;
    }

    public Collection<Label> testTargetsForSourceFile(File sourceFile,
                                                      @Nullable TestIdeInfo.TestSize testSize) {
      BlazeProjectData blazeProjectData = BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
      if (blazeProjectData != null) {
        if (!USE_TEST_SIZE.getValue()) {
          testSize = null;
        }
        // If testSize == null then do a pass preferring small
        // Some test runners will assume no size annotation == small and filter on that, others will not
        else if (testSize == null) {
          Collection<Label> smallResults = testTargetsForSourceFile(
            blazeProjectData.reverseDependencies,
            sourceFile,
            TestIdeInfo.DEFAULT_NON_ANNOTATED_TEST_SIZE);

          if (!smallResults.isEmpty()) {
            return smallResults;
          }
        }

        return testTargetsForSourceFile(blazeProjectData.reverseDependencies, sourceFile, testSize);
      }
      return ImmutableList.of();
    }

    @VisibleForTesting
    Collection<Label> testTargetsForSourceFile(ImmutableMultimap<Label, Label> rdepsMap,
                                               File sourceFile,
                                               @Nullable TestIdeInfo.TestSize testSize) {
      List<Label> result = Lists.newArrayList();
      Collection<Label> roots = rootsMap.get(sourceFile);

      Queue<Label> todo = Queues.newArrayDeque();
      for (Label label : roots) {
        todo.add(label);
      }
      Set<Label> seen = Sets.newHashSet();
      while (!todo.isEmpty()) {
        Label label = todo.remove();
        if (!seen.add(label)) {
          continue;
        }

        RuleIdeInfo rule = ruleMap.get(label);
        if (isTestRule(rule) && matchesTestSize(rule, testSize)) {
          result.add(label);
        }
        for (Label rdep : rdepsMap.get(label)) {
          todo.add(rdep);
        }
      }
      return result;
    }

    static Multimap<File, Label> createRootsMap(Collection<RuleIdeInfo> rules) {
      Multimap<File, Label> result = ArrayListMultimap.create();
      for (RuleIdeInfo ruleIdeInfo : rules) {
        for (ArtifactLocation source : ruleIdeInfo.sources) {
          result.put(source.getFile(), ruleIdeInfo.label);
        }
      }
      return result;
    }

    private static boolean isTestRule(@Nullable RuleIdeInfo rule) {
      return rule != null && rule.kind != null && rule.kind.isOneOf(
        Kind.ANDROID_ROBOLECTRIC_TEST,
        Kind.ANDROID_TEST,
        Kind.JAVA_TEST,
        Kind.GWT_TEST
      );
    }
  }

  private static boolean matchesTestSize(RuleIdeInfo rule, @Nullable TestIdeInfo.TestSize testSize) {
    if (testSize == null) {
      return true;
    }
    TestIdeInfo.TestSize ruleTestSize = TestIdeInfo.getTestSize(rule);
    if (ruleTestSize == null) {
      return true;
    }
    return ruleTestSize == testSize;
  }

  public TestRuleFinderImpl(Project project) {
    this.project = project;
  }

  @Override
  public Collection<Label> testTargetsForSourceFile(File sourceFile, @Nullable TestIdeInfo.TestSize testSize) {
    TestMap testMap = getTestMap();
    if (testMap == null) {
      return ImmutableList.of();
    }
    return testMap.testTargetsForSourceFile(sourceFile, testSize);
  }

  private synchronized TestMap getTestMap() {
    if (testMap == null) {
      testMap = initTestMap();
    }
    return testMap;
  }

  @Nullable
  private TestMap initTestMap() {
    BlazeProjectData blazeProjectData = BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (blazeProjectData == null) {
      return null;
    }
    return new TestMap(project, blazeProjectData.ruleMap);
  }

  private synchronized void clearMapData() {
    this.testMap = null;
  }

  static class ClearTestMap extends SyncListener.Adapter {
    @Override
    public void onSyncComplete(Project project,
                               BlazeImportSettings importSettings,
                               ProjectViewSet projectViewSet,
                               BlazeProjectData blazeProjectData) {
      TestRuleFinder testRuleFinder = TestRuleFinder.getInstance(project);
      ((TestRuleFinderImpl) testRuleFinder).clearMapData();
    }
  }
}
