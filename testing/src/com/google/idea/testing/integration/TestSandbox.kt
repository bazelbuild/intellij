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
package com.google.idea.testing.integration

import org.junit.rules.ExternalResource
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

class TestSandbox : ExternalResource() {

  @Throws(IOException::class)
  override fun before() {
    val sandboxRoot = Path.of(requireNotNull(System.getenv("TEST_TMPDIR")))

    registerSandboxPath(sandboxRoot, "idea.home.path", "home")
    registerSandboxPath(sandboxRoot, "idea.config.path", "config")
    registerSandboxPath(sandboxRoot, "idea.system.path", "system")
    registerSandboxPath(sandboxRoot, "java.util.prefs.userRoot", "userRoot")
    registerSandboxPath(sandboxRoot, "java.util.prefs.systemRoot", "systemRoot")
  }
}

@Throws(IOException::class)
private fun registerSandboxPath(root: Path, property: String, directory: String) {
  val path = root.resolve(directory)
  Files.createDirectories(path)
  System.setProperty(property, path.toString())
}