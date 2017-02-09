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
import java.io.File;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link BlazeTestLogParser}. */
@RunWith(JUnit4.class)
public class BlazeTestLogParserTest {

  @Test
  public void testParseTestXmlLine() {
    assertThat(BlazeTestLogParser.parseXmlLocation("    XML_OUTPUT_FILE=/tmp/test.xml \\"))
        .isEqualTo(new File("/tmp/test.xml"));
  }

  @Test
  public void testNonTestXmlLinesIgnored() {
    assertThat(BlazeTestLogParser.parseXmlLocation("    TEST_TMPDIR=/tmp/test \\")).isNull();
  }

  @Test
  public void testMultipleInputLines() {
    List<String> lines =
        ImmutableList.of(
            "Test command:",
            "cd /build/work/runfiles/workspace && \\",
            "  env - \\",
            "    JAVA_RUNFILES=/build/work/runfiles \\",
            "    PWD=/build/work/runfiles/workspace \\",
            "    XML_OUTPUT_FILE=/tmp/dir/test.xml \\",
            "    USER=username \\");
    assertThat(BlazeTestLogParser.parseTestXmlFile(lines.stream()))
        .isEqualTo(new File("/tmp/dir/test.xml"));
  }
}
