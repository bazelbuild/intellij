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
package com.google.idea.blaze.skylark.repl

import com.google.common.truth.Truth.assertThat
import com.google.idea.balze.skylark.repl.REPL_JAR_PATH
import com.google.idea.common.util.RunJarService
import com.intellij.execution.process.CapturingProcessAdapter
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.runBlocking
import com.google.idea.testing.integration.TestSandbox
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.nio.file.Files

@RunWith(JUnit4::class)
class SkylarkReplTest : BasePlatformTestCase() {

  @Rule
  @JvmField
  val sandbox = TestSandbox()

  @Test
  fun testFindsRepl() {
    assertThat(Files.exists(REPL_JAR_PATH)).isTrue()
  }

  @Test
  fun testRunRepl() {
    val handler = runBlocking { RunJarService.run(REPL_JAR_PATH, "-c", "print('hello world')") }
    val adapter = CapturingProcessAdapter()
    handler.addProcessListener(adapter)
    handler.startNotify()
    handler.waitFor()

    assertThat(adapter.output.stderr).isEmpty()
    assertThat(adapter.output.stdout).isEqualTo("hello world\n")
    assertThat(adapter.output.exitCode).isEqualTo(0)
  }
}
