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
package com.google.idea.blaze.base.run.smrunner;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.run.smrunner.BlazeXmlSchema.TestSuite;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link BlazeXmlSchema}. */
@RunWith(JUnit4.class)
public class BlazeXmlSchemaTest {

  @Test
  public void testNoTestSuitesOuterElement() {
    List<String> lines =
        ImmutableList.of(
            "  <testsuite name=\"foo/bar\" tests=\"1\" time=\"19.268\">",
            "      <testcase name=\"TestName\" result=\"completed\" status=\"run\" time=\"19.2\">",
            "          <system-out>PASS&#xA;&#xA;</system-out>",
            "      </testcase>",
            "  </testsuite>");
    InputStream stream =
        new ByteArrayInputStream(Joiner.on('\n').join(lines).getBytes(StandardCharsets.UTF_8));
    TestSuite parsed = BlazeXmlSchema.parse(stream);
    assertThat(parsed).isNotNull();
  }

  @Test
  public void testOuterTestSuitesElement() {
    List<String> lines =
        ImmutableList.of(
            "<?xml version='1.0' encoding='UTF-8'?>",
            "<testsuites>",
            "  <testsuite name='foo' hostname='localhost' tests='331' failures='0' id='0'>",
            "    <properties />",
            "    <system-out />",
            "    <system-err />",
            "  </testsuite>",
            "  <testsuite name='bar'>",
            "    <testcase name='bar_test_1' time='12.2' />",
            "    <system-out />",
            "  </testsuite>",
            "</testsuites>");
    InputStream stream =
        new ByteArrayInputStream(Joiner.on('\n').join(lines).getBytes(StandardCharsets.UTF_8));
    TestSuite parsed = BlazeXmlSchema.parse(stream);
    assertThat(parsed).isNotNull();
  }
}
