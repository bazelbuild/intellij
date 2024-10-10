/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.dependencies;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.model.primitives.InvalidTargetException;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class BlazeQueryTargetTagFilterTest {

  /**
   * This is the &quot;happy days&quot; test showing the inputs yielding a sensible query.
   */
  @Test
  public void testAssembleQuery() throws InvalidTargetException {
    List<TargetExpression> targetExpressions = ImmutableList.of(
        TargetExpression.fromString("//flamingos:lib"),
        TargetExpression.fromString("//flamingos:bin")
    );

    Set<String> tags = ImmutableSet.of("sea-weed", "lake-weed");

    // code under test
    String actualQueryString = BlazeQueryTargetTagFilter.getQueryString(targetExpressions, tags);

    String expectedQueryString = "attr(\"tags\", \"[\\[ ](lake-weed|sea-weed)[,\\]]\", //flamingos:lib + //flamingos:bin)";
    assertThat(actualQueryString).isEqualTo(expectedQueryString);
  }

  /**
   * This test shows what happens if the logic attempts to create a query for a tag that might break
   * the regular expression assembly.
   */
  @Test(expected = IllegalStateException.class)
  public void testAssembleQueryWithBadTag() throws InvalidTargetException {
    List<TargetExpression> targetExpressions = ImmutableList.of(TargetExpression.fromString("//:bin"));

    Set<String> tags = ImmutableSet.of("far.out");
    // ^^ note the dot is a special character in regex so is disallowed

    // code under test
    BlazeQueryTargetTagFilter.getQueryString(targetExpressions, tags);

    // The expected behaviour is that this will exception because of the malformed tag.
  }

}
