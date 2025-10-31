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
package com.google.idea.blaze.aspect

import com.google.common.base.Splitter
import com.google.devtools.intellij.IntellijAspectTestFixtureOuterClass.IntellijAspectTestFixture
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo.CIdeInfo
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo.TargetIdeInfo
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo.TargetKey
import org.junit.rules.ExternalResource
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.file.Path
import java.nio.file.Paths

/**
 * JUnit resource for loading and accessing intellij aspect test fixtures.
 * Prefer using this as JUnit rule over extending `BazelIntellijAspectTest`
 * directly.
 */
class IntellijAspectResource (private val testCtx: Class<*>, private val fixtureLabel: String) : ExternalResource() {

  /**
   * Derives the fixture name from the context class. Use this constructor when
   * using the `intellij_aspect_test` bazel macro.
   */
  constructor(ctx: Class<*>) : this(ctx, ":${ctx.simpleName}_fixture")

  private lateinit var fixture: IntellijAspectTestFixture

  override fun before() {
    fixture = loadAspectFixture(testCtx, fixtureLabel)
  }

  fun findTargets(
    label: String,
    externalRepo: String? = null,
    fractionalAspectIds: List<String> = emptyList(),
  ): List<TargetIdeInfo> {
    return fixture.targetsList.filter { matchTarget(it, testCtx, label, externalRepo, fractionalAspectIds) }
  }

  fun findTarget(
    label: String,
    externalRepo: String? = null,
    fractionalAspectIds: List<String> = emptyList(),
  ): TargetIdeInfo {
    return requireNotNull(findTargets(label, externalRepo, fractionalAspectIds).firstOrNull()) {
      "target not found: $label"
    }
  }

  fun findCIdeInfo(
    label: String,
    externalRepo: String? = null,
    fractionalAspectIds: List<String> = emptyList(),
  ): CIdeInfo {
    val target = findTarget(label, externalRepo, fractionalAspectIds)
    require(target.hasCIdeInfo()) { "target has no c_ide_info: $label" }

    return target.cIdeInfo
  }
}

@Throws(IOException::class)
private fun loadAspectFixture(ctx: Class<*>, relativeLabel: String): IntellijAspectTestFixture {
  val label = testRelativeLabel(ctx, relativeLabel)
  val relativePath = (label.replace(':', '/') + ".intellij-aspect-test-fixture").substring(2)
  val runfilesPath = runfilesPath(relativePath).toFile()

  FileInputStream(runfilesPath).use { inputStream ->
    return IntellijAspectTestFixture.parseFrom(inputStream)
  }
}

/**
 * Converts a relative label to a test relative label. The package of the
 * context class is used as the base path.
 */
private fun testRelativeLabel(ctx: Class<*>, relativeLabel: String): String {
  require(relativeLabel.startsWith(':'))

  val basePath = Splitter.on("/com/").splitToList(System.getenv("TEST_BINARY"))[0]
  val packagePath = Paths.get(basePath, ctx.getPackage().name.replace(".", File.separator));

  return "//$packagePath$relativeLabel"
}

private fun runfilesPath(relativePath: String?): Path {
  return Paths.get(getUserValue("TEST_SRCDIR"), getUserValue("TEST_WORKSPACE"), relativePath)
}

private fun getUserValue(name: String): String {
  return requireNotNull(System.getProperty(name) ?: return System.getenv(name)) {
    "$name environment variable or property not found"
  }
}

/**
 * Matches a target key, see [matchLabel] and [matchAspectIds] for details.
 */
private fun matchTarget(
  info: TargetIdeInfo,
  ctx: Class<*>,
  label: String,
  externalRepo: String?,
  fractionalAspectIds: List<String>,
): Boolean {
  return info.hasKey()
      && matchLabel(info.key, ctx, label, externalRepo)
      && matchAspectIds(info.key, fractionalAspectIds)
}

/**
 * Matches target key against a label. If the label is relative it is treated
 * as a test relative label. If a external repo is specified the label must be
 * absolute with regard to that repo.
 */
private fun matchLabel(key: TargetKey, ctx: Class<*>, label: String, externalRepo: String?): Boolean {
  if (externalRepo != null) {
    require(label.startsWith("//")) { "external repo label must be absolute: $label" }
    return key.label.endsWith("$externalRepo$label")
  }
  if (label.startsWith(':')) {
    return key.label == testRelativeLabel(ctx, label)
  }

  return key.label == label
}

/**
 * Matches a target key against a list of partial target keys. Returns true if
 * any of the partial keys match or the list is empty.
 */
private fun matchAspectIds(key: TargetKey, fractionalAspectIds: List<String>): Boolean {
  if (fractionalAspectIds.isEmpty()) return true

  for (aspectId in key.aspectIdsList) {
    if (key.aspectIdsList.any { it.contains(aspectId) }) return true
  }

  return false
}