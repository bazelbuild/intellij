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

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableMultimap;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.RuleIdeInfo;
import com.google.idea.blaze.base.ideinfo.RuleKey;
import com.google.idea.blaze.base.ideinfo.RuleMap;
import com.google.idea.blaze.base.ideinfo.RuleMapBuilder;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.rulemaps.ReverseDependencyMap;
import com.google.idea.blaze.base.run.testmap.TestRuleFinderImpl.TestMap;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.common.experiments.ExperimentService;
import com.google.idea.common.experiments.MockExperimentService;
import java.io.File;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for the test map */
@RunWith(JUnit4.class)
public class TestMapTest extends BlazeTestCase {
  private RuleMapBuilder ruleMapBuilder;

  private final ArtifactLocationDecoder artifactLocationDecoder =
      (ArtifactLocationDecoder)
          artifactLocation -> new File("/", artifactLocation.getRelativePath());

  @Override
  protected void initTest(
      @NotNull Container applicationServices, @NotNull Container projectServices) {
    super.initTest(applicationServices, projectServices);
    applicationServices.register(ExperimentService.class, new MockExperimentService());
    ruleMapBuilder = RuleMapBuilder.builder();
  }

  @Test
  public void testTrivialTestMap() throws Exception {
    RuleMap ruleMap =
        ruleMapBuilder
            .addRule(
                RuleIdeInfo.builder()
                    .setBuildFile(sourceRoot("test/BUILD"))
                    .setLabel("//test:test")
                    .setKind("java_test")
                    .addSource(sourceRoot("test/Test.java")))
            .build();

    TestMap testMap = new TestMap(project, artifactLocationDecoder, ruleMap);
    ImmutableMultimap<RuleKey, RuleKey> reverseDependencies =
        ReverseDependencyMap.createRdepsMap(ruleMap);
    assertThat(testMap.testTargetsForSourceFile(reverseDependencies, new File("/test/Test.java")))
        .containsExactly(new Label("//test:test"));
  }

  @Test
  public void testOneStepRemovedTestMap() throws Exception {
    RuleMap ruleMap =
        ruleMapBuilder
            .addRule(
                RuleIdeInfo.builder()
                    .setBuildFile(sourceRoot("test/BUILD"))
                    .setLabel("//test:test")
                    .setKind("java_test")
                    .addDependency("//test:lib"))
            .addRule(
                RuleIdeInfo.builder()
                    .setBuildFile(sourceRoot("test/BUILD"))
                    .setLabel("//test:lib")
                    .setKind("java_library")
                    .addSource(sourceRoot("test/Test.java")))
            .build();

    TestMap testMap = new TestMap(project, artifactLocationDecoder, ruleMap);
    ImmutableMultimap<RuleKey, RuleKey> reverseDependencies =
        ReverseDependencyMap.createRdepsMap(ruleMap);
    assertThat(testMap.testTargetsForSourceFile(reverseDependencies, new File("/test/Test.java")))
        .containsExactly(new Label("//test:test"));
  }

  @Test
  public void testTwoCandidatesTestMap() throws Exception {
    RuleMap ruleMap =
        ruleMapBuilder
            .addRule(
                RuleIdeInfo.builder()
                    .setBuildFile(sourceRoot("test/BUILD"))
                    .setLabel("//test:test")
                    .setKind("java_test")
                    .addDependency("//test:lib"))
            .addRule(
                RuleIdeInfo.builder()
                    .setBuildFile(sourceRoot("test/BUILD"))
                    .setLabel("//test:test2")
                    .setKind("java_test")
                    .addDependency("//test:lib"))
            .addRule(
                RuleIdeInfo.builder()
                    .setBuildFile(sourceRoot("test/BUILD"))
                    .setLabel("//test:lib")
                    .setKind("java_library")
                    .addSource(sourceRoot("test/Test.java")))
            .build();

    TestMap testMap = new TestMap(project, artifactLocationDecoder, ruleMap);
    ImmutableMultimap<RuleKey, RuleKey> reverseDependencies =
        ReverseDependencyMap.createRdepsMap(ruleMap);
    assertThat(testMap.testTargetsForSourceFile(reverseDependencies, new File("/test/Test.java")))
        .containsExactly(new Label("//test:test"), new Label("//test:test2"));
  }

  @Test
  public void testBfsPreferred() throws Exception {
    RuleMap ruleMap =
        ruleMapBuilder
            .addRule(
                RuleIdeInfo.builder()
                    .setBuildFile(sourceRoot("test/BUILD"))
                    .setLabel("//test:lib")
                    .setKind("java_library")
                    .addSource(sourceRoot("test/Test.java")))
            .addRule(
                RuleIdeInfo.builder()
                    .setBuildFile(sourceRoot("test/BUILD"))
                    .setLabel("//test:lib2")
                    .setKind("java_library")
                    .addDependency("//test:lib"))
            .addRule(
                RuleIdeInfo.builder()
                    .setBuildFile(sourceRoot("test/BUILD"))
                    .setLabel("//test:test2")
                    .setKind("java_test")
                    .addDependency("//test:lib2"))
            .addRule(
                RuleIdeInfo.builder()
                    .setBuildFile(sourceRoot("test/BUILD"))
                    .setLabel("//test:test")
                    .setKind("java_test")
                    .addDependency("//test:lib"))
            .build();

    TestMap testMap = new TestMap(project, artifactLocationDecoder, ruleMap);
    ImmutableMultimap<RuleKey, RuleKey> reverseDependencies =
        ReverseDependencyMap.createRdepsMap(ruleMap);
    assertThat(testMap.testTargetsForSourceFile(reverseDependencies, new File("/test/Test.java")))
        .containsExactly(new Label("//test:test"), new Label("//test:test2"))
        .inOrder();
  }

  @Test
  public void testSourceIncludedMultipleTimesFindsAll() throws Exception {
    RuleMap ruleMap =
        ruleMapBuilder
            .addRule(
                RuleIdeInfo.builder()
                    .setBuildFile(sourceRoot("test/BUILD"))
                    .setLabel("//test:test")
                    .setKind("java_test")
                    .addDependency("//test:lib"))
            .addRule(
                RuleIdeInfo.builder()
                    .setBuildFile(sourceRoot("test/BUILD"))
                    .setLabel("//test:test2")
                    .setKind("java_test")
                    .addDependency("//test:lib2"))
            .addRule(
                RuleIdeInfo.builder()
                    .setBuildFile(sourceRoot("test/BUILD"))
                    .setLabel("//test:lib")
                    .setKind("java_library")
                    .addSource(sourceRoot("test/Test.java")))
            .addRule(
                RuleIdeInfo.builder()
                    .setBuildFile(sourceRoot("test/BUILD"))
                    .setLabel("//test:lib2")
                    .setKind("java_library")
                    .addSource(sourceRoot("test/Test.java")))
            .build();

    TestMap testMap = new TestMap(project, artifactLocationDecoder, ruleMap);
    ImmutableMultimap<RuleKey, RuleKey> reverseDependencies =
        ReverseDependencyMap.createRdepsMap(ruleMap);
    assertThat(testMap.testTargetsForSourceFile(reverseDependencies, new File("/test/Test.java")))
        .containsExactly(new Label("//test:test"), new Label("//test:test2"));
  }

  @Test
  public void testSourceIncludedMultipleTimesShouldOnlyGiveOneInstanceOfTest() throws Exception {
    RuleMap ruleMap =
        ruleMapBuilder
            .addRule(
                RuleIdeInfo.builder()
                    .setBuildFile(sourceRoot("test/BUILD"))
                    .setLabel("//test:test")
                    .setKind("java_test")
                    .addDependency("//test:lib")
                    .addDependency("//test:lib2"))
            .addRule(
                RuleIdeInfo.builder()
                    .setBuildFile(sourceRoot("test/BUILD"))
                    .setLabel("//test:lib")
                    .setKind("java_library")
                    .addSource(sourceRoot("test/Test.java")))
            .addRule(
                RuleIdeInfo.builder()
                    .setBuildFile(sourceRoot("test/BUILD"))
                    .setLabel("//test:lib2")
                    .setKind("java_library")
                    .addSource(sourceRoot("test/Test.java")))
            .build();

    TestMap testMap = new TestMap(project, artifactLocationDecoder, ruleMap);
    ImmutableMultimap<RuleKey, RuleKey> reverseDependencies =
        ReverseDependencyMap.createRdepsMap(ruleMap);
    assertThat(testMap.testTargetsForSourceFile(reverseDependencies, new File("/test/Test.java")))
        .containsExactly(new Label("//test:test"));
  }

  private ArtifactLocation sourceRoot(String relativePath) {
    return ArtifactLocation.builder()
        .setRelativePath(relativePath)
        .setIsSource(true)
        .build();
  }
}
