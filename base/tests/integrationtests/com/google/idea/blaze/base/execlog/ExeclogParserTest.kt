/*
 * Copyright 2026 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.execlog

import com.google.common.truth.Truth.assertThat
import com.google.idea.common.util.RunJarService
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.nio.file.Files

@RunWith(JUnit4::class)
class ExeclogParserTest : BasePlatformTestCase() {

  @Test
  fun testFindsParser() {
    assertThat(Files.exists(PARSER_JAR_PATH)).isTrue()
  }

  @Test
  fun testRunParser() {
    val output = runBlocking { RunJarService.capture(PARSER_JAR_PATH, "--help") }
    assertThat(output.exitCode).isEqualTo(0)
  }
}
