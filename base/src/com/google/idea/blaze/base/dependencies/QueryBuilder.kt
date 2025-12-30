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

import com.google.idea.blaze.base.model.primitives.TargetExpression

private const val MANUAL_TAG = "manual"
private const val NO_IDE_TAG = "no-ide"

class QueryBuilder {
  private val includedTargets = mutableListOf<String>()
  private val excludedTargets = mutableListOf<String>()

  private val excludedTags = mutableListOf<String>()

  fun excludeTarget(target: TargetExpression): QueryBuilder {
    excludedTargets.add(target.toString().removePrefix("-"))
    return this
  }

  fun excludeTargets(targets: Collection<TargetExpression>): QueryBuilder {
    targets.forEach(::excludeTarget)
    return this
  }

  fun includeTarget(target: TargetExpression): QueryBuilder {
    if (target.isExcluded) {
      excludeTarget(target)
    } else {
      includedTargets.add(target.toString())
    }

    return this
  }

  fun includeTargets(targets: Collection<TargetExpression>): QueryBuilder {
    targets.forEach(::includeTarget)
    return this
  }

  fun excludeManualTag(condition: Boolean = true): QueryBuilder {
    if (condition) {
      excludedTags.add(MANUAL_TAG)
    }
    return this
  }

  fun excludeNoIdeTag(condition: Boolean = true): QueryBuilder {
    if (condition) {
      excludedTags.add(NO_IDE_TAG)
    }
    return this
  }

  fun isEmpty(): Boolean {
    return includedTargets.isEmpty()
  }

  fun build(): String {
    if (includedTargets.isEmpty()) {
      throw IllegalStateException("no targets included in query")
    }

    var query = includedTargets.joinToString(separator = "+") { "'$it'" }

    if (excludedTargets.isNotEmpty()) {
      val expression = excludedTargets.joinToString(separator = "-") { "'$it'" }
      query = "$query-$expression"
    }

    if (excludedTags.isNotEmpty()) {
      val expression = excludedTags.joinToString(separator = "|") { "($it)" }
      query = "attr('tags','^((?!($expression)).)*$',$query)"
    }

    return query
  }
}