/*
 * Copyright 2025 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.dependencies

import com.google.common.truth.Truth
import com.google.idea.blaze.base.model.primitives.TargetExpression
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class QueryBuilderTest {

  @Test
  fun emptyQueryThrows() {
    assertThrows(IllegalStateException::class.java) {
      QueryBuilder().build()
    }

    assertThrows(IllegalStateException::class.java) {
      QueryBuilder().excludeTarget("//some:target".asTargetExpression()).build()
    }
  }

  @Test
  fun includeManyTargets() {
    val query = QueryBuilder()
      .includeTarget("//some:target1".asTargetExpression())
      .includeTarget("//some:target2".asTargetExpression())
      .build()

    Truth.assertThat(query).isEqualTo("'//some:target1'+'//some:target2'")
  }

  @Test
  fun excludeManyTargets() {
    val query = QueryBuilder()
      .includeTarget("//some:target1".asTargetExpression())
      .includeTarget("//some:target2".asTargetExpression())
      .excludeTarget("//some:target3".asTargetExpression())
      .excludeTarget("//some:target4".asTargetExpression())
      .build()

    Truth.assertThat(query).isEqualTo("'//some:target1'+'//some:target2'-'//some:target3'-'//some:target4'")
  }

  @Test
  fun excludeTags() {
    val query = QueryBuilder()
      .includeTarget("//some:target".asTargetExpression())
      .excludeManualTag()
      .excludeNoIdeTag()
      .build()

    Truth.assertThat(query).isEqualTo("attr('tags','^((?!((manual)|(no-ide))).)*$','//some:target')")
  }

  private fun String.asTargetExpression(): TargetExpression {
    return TargetExpression.fromString(this)
  }

  private fun assertThrows(exception: Class<*>, block: () -> Unit) {
    try {
      block()
      fail("expected exception of type $exception")
    } catch (e: Exception) {
      Truth.assertThat(e).isInstanceOf(exception)
    }
  }
}