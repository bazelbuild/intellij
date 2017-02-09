/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.run.testlogs;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.model.primitives.Label;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link BlazeCommandLogParser}. */
@RunWith(JUnit4.class)
public class BlazeCommandLogParserTest {

  @Test
  public void testParseTestXmlLine() {
    assertThat(BlazeCommandLogParser.parseTestTarget("//path/to:target    PASSED in 5.3s"))
        .isEqualTo(new Label("//path/to:target"));

    assertThat(BlazeCommandLogParser.parseTestTarget("//path/to:target    FAILED in 5.3s"))
        .isEqualTo(new Label("//path/to:target"));

    assertThat(BlazeCommandLogParser.parseTestTarget("//path/to:target (cached) PASSED in 5.3s"))
        .isEqualTo(new Label("//path/to:target"));
  }

  @Test
  public void testNonTestXmlLinesIgnored() {
    assertThat(BlazeCommandLogParser.parseTestTarget("Executed 0 out of 1 test: 1 test passes."))
        .isNull();
    assertThat(BlazeCommandLogParser.parseTestTarget("INFO: Found 8 test targets...")).isNull();
    assertThat(BlazeCommandLogParser.parseTestTarget("Target //golang:unit_tests up-to-date:"))
        .isNull();
    assertThat(BlazeCommandLogParser.parseTestTarget("  bazel-bin/golang/unit_tests.jar")).isNull();
  }

  @Test
  public void testMultipleInputLines() {
    List<String> lines =
        ImmutableList.of(
            "INFO: Found 3 test targets...",
            "INFO: Elapsed time: 3.239s, Critical Path: 1.65s",
            "//base:integration_tests                                (cached) PASSED in 27.9s",
            "//base:unit_tests                                       (cached) PASSED in 4.3s",
            "//golang:unit_tests                                              FAILED in 0.6s",
            "Executed 1 out of 3 test: 2 test passes.");
    assertThat(BlazeCommandLogParser.parseTestTargets(lines.stream()))
        .containsExactly(
            new Label("//base:integration_tests"),
            new Label("//base:unit_tests"),
            new Label("//golang:unit_tests"));
  }
}
