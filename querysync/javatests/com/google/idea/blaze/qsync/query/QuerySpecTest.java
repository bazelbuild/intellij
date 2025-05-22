/*
 * Copyright 2023-2025 The Bazel Authors. All rights reserved.
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

import com.google.idea.blaze.qsync.BlazeQueryParser;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class QuerySpecTest {

  @Test
  public void testGetQueryExpression_includes_singlePath() {
    QuerySpec qs =
      QuerySpec.builder(QuerySpec.QueryStrategy.PLAIN)
        .workspaceRoot(Path.of("/workspace/"))
        .includePath(Path.of("some/included/path"))
        .supportedRuleClasses(BlazeQueryParser.getAllSupportedRuleClasses())
        .build();
    assertThat(qs.getQueryExpression()).hasValue("let base = //some/included/path/...:*\n" +
        "in $base - attr(\"tags\", \"[\\[,]no-ide[\\],]\", $base)");
  }

  @Test
  public void testGetQueryExpression_empty_query() {
    QuerySpec qs = QuerySpec.builder(QuerySpec.QueryStrategy.PLAIN).workspaceRoot(Path.of("/workspace/"))
      .supportedRuleClasses(BlazeQueryParser.getAllSupportedRuleClasses())
      .build();
    assertThat(qs.getQueryExpression()).isEmpty();
  }

  @Test
  public void testGetQueryExpression_experimental_empty_query() {
    QuerySpec qs = QuerySpec.builder(QuerySpec.QueryStrategy.FILTERING_TO_KNOWN_AND_USED_TARGETS
      ).workspaceRoot(Path.of("/workspace/"))
      .supportedRuleClasses(BlazeQueryParser.getAllSupportedRuleClasses())
      .build();
    assertThat(qs.getQueryExpression()).isEmpty();
  }

  @Test
  public void testGetQueryExpression_includes_multiplePaths() {
    QuerySpec qs =
      QuerySpec.builder(QuerySpec.QueryStrategy.PLAIN)
        .workspaceRoot(Path.of("/workspace/"))
        .includePath(Path.of("some/included/path"))
        .includePath(Path.of("another/included/path"))
        .supportedRuleClasses(BlazeQueryParser.getAllSupportedRuleClasses())
        .build();
    assertThat(qs.getQueryExpression())
      .hasValue("let base = //some/included/path/...:* + //another/included/path/...:*\n" +
          "in $base - attr(\"tags\", \"[\\[,]no-ide[\\],]\", $base)");
  }

  @Test
  public void testGetQueryExpression_includes_and_excludes() {
    QuerySpec qs =
      QuerySpec.builder(QuerySpec.QueryStrategy.PLAIN)
        .workspaceRoot(Path.of("/workspace/"))
        .includePath(Path.of("some/included/path"))
        .includePath(Path.of("another/included/path"))
        .excludePath(Path.of("some/included/path/excluded"))
        .excludePath(Path.of("another/included/path/excluded"))
        .supportedRuleClasses(BlazeQueryParser.getAllSupportedRuleClasses())
        .build();
    assertThat(qs.getQueryExpression())
      .hasValue("let base = //some/included/path/...:* + //another/included/path/...:* - //some/included/path/excluded/...:* - //another/included/path/excluded/...:*\n" +
          "in $base - attr(\"tags\", \"[\\[,]no-ide[\\],]\", $base)");
  }

  @Test
  public void testGetQueryExpression_experimental_includes_and_excludes() {
    String kindsExpression = String.join("|",
        "_iml_module_", "_java_grpc_library", "_java_lite_grpc_library",
        "_kotlin_library", "android_binary", "android_instrumentation_test", "android_library",
        "android_local_test", "cc_binary", "cc_library", "cc_shared_library", "cc_test",
        "intellij_plugin_debug_target", "java_binary", "java_library", "java_lite_proto_library",
        "java_mutable_proto_library", "java_proto_library", "java_test", "kt_android_library_helper",
        "kt_jvm_binary", "kt_jvm_library", "kt_jvm_library_helper", "kt_native_library", "proto_library",
        "py_binary", "py_library", "py_test", "thrift_library");

    QuerySpec qs =
      QuerySpec.builder(QuerySpec.QueryStrategy.FILTERING_TO_KNOWN_AND_USED_TARGETS)
        .workspaceRoot(Path.of("/workspace/"))
        .includePath(Path.of("some/included/path"))
        .includePath(Path.of("another/included/path"))
        .excludePath(Path.of("some/included/path/excluded"))
        .excludePath(Path.of("another/included/path/excluded"))
        .supportedRuleClasses(BlazeQueryParser.getAllSupportedRuleClasses())
        .build();

    assertThat(qs.getQueryExpression())
      .hasValue(
        "let base = //some/included/path/...:* + //another/included/path/...:* - //some/included/path/excluded/...:* - //another/included/path/excluded/...:*\n" +
        " in let known = kind(\"source file|(_transition_)?(" + kindsExpression + ")\", $base) \n" +
        " in let unknown = $base except $known \n" +
        " in $known union ($base intersect allpaths($known, $unknown)) \n");
  }
}
