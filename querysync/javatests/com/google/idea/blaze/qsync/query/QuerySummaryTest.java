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
package com.google.idea.blaze.qsync.query;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.idea.blaze.qsync.query.QuerySummaryTestUtil.createProtoForPackages;

import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.qsync.testdata.TestData;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class QuerySummaryTest {

  private String targetName(String buildTarget) {
    return buildTarget.substring(buildTarget.indexOf(':') + 1);
  }

  @Test
  public void testCreate_javaLibrary_noDeps() throws IOException {
    QuerySummary qs =
        QuerySummary.create(TestData.getPathFor(TestData.JAVA_LIBRARY_NO_DEPS_QUERY).toFile());
    assertThat(qs.getProto().getRulesMap().keySet())
        .containsExactly(TestData.ROOT_PACKAGE + "/nodeps:nodeps");
    Query.Rule rule = qs.getProto().getRulesMap().get(TestData.ROOT_PACKAGE + "/nodeps:nodeps");
    assertThat(rule.getRuleClass()).isEqualTo("java_library");
    assertThat(rule.getSourcesCount()).isEqualTo(1);
    assertThat(targetName(rule.getSources(0))).isEqualTo("TestClassNoDeps.java");
    assertThat(rule.getDepsCount()).isEqualTo(0);
    assertThat(rule.getIdlSourcesCount()).isEqualTo(0);
    assertThat(qs.getProto().getSourceFilesMap().keySet())
        .containsExactly(
            TestData.ROOT_PACKAGE + "/nodeps:TestClassNoDeps.java",
            TestData.ROOT_PACKAGE + "/nodeps:BUILD");
  }

  @Test
  public void testGetPackages_singleRule() {
    QuerySummary summary = new QuerySummary(createProtoForPackages("//my/build/package:rule"));
    assertThat(summary.getPackages()).containsExactly(Path.of("my/build/package"));
  }

  @Test
  public void testGetPackages_multiRule_onePackage() {
    QuerySummary summary =
        new QuerySummary(
            createProtoForPackages("//my/build/package:rule1", "//my/build/package:rule2"));
    assertThat(summary.getPackages()).containsExactly(Path.of("my/build/package"));
  }

  @Test
  public void testGetPackages_multiRule_multiPackage() {
    QuerySummary summary =
        new QuerySummary(
            createProtoForPackages(
                "//my/build/package:rule1",
                "//my/build/package:rule2",
                "//my/build/package2:rule1",
                "//my/build/package2:rule2"));
    assertThat(summary.getPackages())
        .containsExactly(Path.of("my/build/package"), Path.of("my/build/package2"));
  }

  @Test
  public void testGetParentPackage_noparent() {
    QuerySummary summary = new QuerySummary(createProtoForPackages("//my/build/package:rule"));
    assertThat(summary.getParentPackage(Path.of("my/build/package"))).isEmpty();
  }

  @Test
  public void testGetParentPackage_directParent() {
    QuerySummary summary =
        new QuerySummary(
            createProtoForPackages(
                "//my/build/package:rule", "//my/build/package/subpackage:rule"));
    assertThat(summary.getParentPackage(Path.of("my/build/package/subpackage")))
        .hasValue(Path.of("my/build/package"));
  }

  @Test
  public void testGetParentPackage_indirectParent() {
    QuerySummary summary =
        new QuerySummary(
            createProtoForPackages("//my/build/package:rule", "//my/build/package/sub1/sub2:rule"));
    assertThat(summary.getParentPackage(Path.of("my/build/package/sub1/sub2")))
        .hasValue(Path.of("my/build/package"));
  }

  @Test
  public void testDelta_replacePackage() {
    QuerySummary base =
        new QuerySummary(
            Query.Summary.newBuilder()
                .putRules(
                    "//my/build/package1:rule",
                    Query.Rule.newBuilder()
                        .setRuleClass("java_library")
                        .addSources("//my/build/package1:Class1.java")
                        .build())
                .putSourceFiles(
                    "//my/build/package1:Class1.java",
                    Query.SourceFile.newBuilder()
                        .setLocation("my/build/package1/Class1.java:1:1")
                        .build())
                .putSourceFiles(
                    "//my/build/package1:subpackage/AnotherClass.java",
                    Query.SourceFile.newBuilder()
                        .setLocation("my/build/package1/subpackage/AnotherClass.java:1:1")
                        .build())
                .putSourceFiles(
                    "//my/build/package1:BUILD",
                    Query.SourceFile.newBuilder()
                        .setLocation("my/build/package1/BUILD:1:1")
                        .build())
                .putRules(
                    "//my/build/package2:rule",
                    Query.Rule.newBuilder()
                        .setRuleClass("java_library")
                        .addSources("//my/build/package2:Class2.java")
                        .build())
                .putSourceFiles(
                    "//my/build/package2:Class2.java",
                    Query.SourceFile.newBuilder()
                        .setLocation("my/build/package2/Class2.java:1:1")
                        .build())
                .putSourceFiles(
                    "//my/build/package2:BUILD",
                    Query.SourceFile.newBuilder()
                        .setLocation("my/build/package2/BUILD:1:1")
                        .build())
                .build());
    QuerySummary delta =
        new QuerySummary(
            Query.Summary.newBuilder()
                .putRules(
                    "//my/build/package1:newrule",
                    Query.Rule.newBuilder()
                        .setRuleClass("java_library")
                        .addSources("//my/build/package1:NewClass.java")
                        .build())
                .putSourceFiles(
                    "//my/build/package1:NewClass.java",
                    Query.SourceFile.newBuilder()
                        .setLocation("my/build/package1/NewClass.java:1:1")
                        .build())
                .putSourceFiles(
                    "//my/build/package1:BUILD",
                    Query.SourceFile.newBuilder().setLocation("my/build/package1/BUILD").build())
                .build());
    QuerySummary applied = base.applyDelta(delta, ImmutableSet.of());
    assertThat(applied.getProto().getRulesMap().keySet())
        .containsExactly("//my/build/package1:newrule", "//my/build/package2:rule");
    assertThat(applied.getProto().getSourceFilesMap().keySet())
        .containsExactly(
            "//my/build/package1:NewClass.java",
            "//my/build/package1:BUILD",
            "//my/build/package2:Class2.java",
            "//my/build/package2:BUILD");
  }

  @Test
  public void testDelta_deletePackage() {
    QuerySummary base =
        new QuerySummary(
            Query.Summary.newBuilder()
                .putRules(
                    "//my/build/package1:rule",
                    Query.Rule.newBuilder()
                        .setRuleClass("java_library")
                        .addSources("//my/build/package1:Class1.java")
                        .build())
                .putSourceFiles(
                    "//my/build/package1:Class1.java",
                    Query.SourceFile.newBuilder()
                        .setLocation("my/build/package1/Class1.java:1:1")
                        .build())
                .putSourceFiles(
                    "//my/build/package1:subpackage/AnotherClass.java",
                    Query.SourceFile.newBuilder()
                        .setLocation("my/build/package1/subpackage/AnotherClass.java:1:1")
                        .build())
                .putSourceFiles(
                    "//my/build/package1:BUILD",
                    Query.SourceFile.newBuilder()
                        .setLocation("my/build/package1/BUILD:1:1")
                        .build())
                .putRules(
                    "//my/build/package2:rule",
                    Query.Rule.newBuilder()
                        .setRuleClass("java_library")
                        .addSources("//my/build/package2:Class2.java")
                        .build())
                .putSourceFiles(
                    "//my/build/package2:Class2.java",
                    Query.SourceFile.newBuilder()
                        .setLocation("my/build/package2/Class2.java:1:1")
                        .build())
                .putSourceFiles(
                    "//my/build/package2:BUILD",
                    Query.SourceFile.newBuilder()
                        .setLocation("my/build/package2/BUILD:1:1")
                        .build())
                .build());
    QuerySummary applied =
        base.applyDelta(QuerySummary.EMPTY, ImmutableSet.of(Path.of("my/build/package1")));
    assertThat(applied.getProto().getRulesMap().keySet())
        .containsExactly("//my/build/package2:rule");
    assertThat(applied.getProto().getSourceFilesMap().keySet())
        .containsExactly("//my/build/package2:Class2.java", "//my/build/package2:BUILD");
  }

  @Test
  public void testDelta_addPackage() {
    QuerySummary base =
        new QuerySummary(
            Query.Summary.newBuilder()
                .putRules(
                    "//my/build/package1:rule",
                    Query.Rule.newBuilder()
                        .setRuleClass("java_library")
                        .addSources("//my/build/package1:Class1.java")
                        .build())
                .putSourceFiles(
                    "//my/build/package1:Class1.java",
                    Query.SourceFile.newBuilder()
                        .setLocation("my/build/package1/Class1.java:1:1")
                        .build())
                .putSourceFiles(
                    "//my/build/package1:BUILD",
                    Query.SourceFile.newBuilder()
                        .setLocation("my/build/package1/BUILD:1:1")
                        .build())
                .build());
    QuerySummary delta =
        new QuerySummary(
            Query.Summary.newBuilder()
                .putRules(
                    "//my/build/package2:rule",
                    Query.Rule.newBuilder()
                        .setRuleClass("java_library")
                        .addSources("//my/build/package2:Class2.java")
                        .build())
                .putSourceFiles(
                    "//my/build/package2:Class2.java",
                    Query.SourceFile.newBuilder()
                        .setLocation("my/build/package2/Class2.java:1:1")
                        .build())
                .putSourceFiles(
                    "//my/build/package2:BUILD",
                    Query.SourceFile.newBuilder()
                        .setLocation("my/build/package2/BUILD:1:1")
                        .build())
                .build());
    QuerySummary applied = base.applyDelta(delta, ImmutableSet.of());
    assertThat(applied.getProto().getRulesMap().keySet())
        .containsExactly("//my/build/package1:rule", "//my/build/package2:rule");
    assertThat(applied.getProto().getSourceFilesMap().keySet())
        .containsExactly(
            "//my/build/package1:Class1.java",
            "//my/build/package1:BUILD",
            "//my/build/package2:Class2.java",
            "//my/build/package2:BUILD");
  }
}
