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
package com.google.idea.blaze.base.sync.aspects.storage

import com.google.common.collect.ImmutableList
import com.google.idea.blaze.base.sync.SyncProjectState
import com.google.idea.blaze.base.sync.SyncScope.SyncFailedException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

private const val DEFAULT_FUNCTION_TEMPLATE = """
def %s(ctx, target):
    return %s
"""

private const val MODULES_DIRECTORY = "modules"

/**
 * Write for one aspect module.
 *
 * An aspect module is a collection of functions that can declare different
 * dependencies. Only if all dependencies are fulfilled will the aspect module
 * be written, otherwise only stubs will be generated.
 */
abstract class AspectModuleWriter : AspectWriter {

  /**
   * A dependency that must be fulfilled for the aspect module to be written.
   * Evaluated over the current sync state of the project.
   */
  protected fun interface Dependency {

    fun isFulfilled(state: SyncProjectState): Boolean
  }

  /**
   * Dependency on a specific rule set (e.g. rules_cc). Requires bazel 7+ and
   * only supports bazelmod projects.
   */
  protected fun ruleSetDependency(name: String) = Dependency { state ->
    state.externalWorkspaceData?.getByRepoName(name) != null
  }

  /**
   * Dependency on a specific registry key.
   */
  protected fun registryKeyDependency(key: String) = Dependency {
    Registry.`is`(key)
  }

  /**
   * Dependency on a specific bazel version.
   */
  protected fun bazelDependency(minVersion: Int) = Dependency { state ->
    state.blazeVersionData.bazelIsAtLeastVersion(minVersion, 0, 0)
  }

  /**
   * All dependencies that must be fulfilled for the aspect module to be written.
   */
  protected abstract fun dependencies(): ImmutableList<Dependency>

  /**
   * A public function exposed by the module.
   *
   * If any dependency is not fulfilled, the provided default value will be used
   * to generate a stub for the function.
   */
  protected data class Function(val name: String, val defaultValue: String)

  /**
   * All functions exposed by the module.
   */
  protected abstract fun functions(): ImmutableList<Function>

  @Throws(IOException::class)
  private fun copyImplementation(dst: Path) {
    Files.createDirectories(dst.resolve(MODULES_DIRECTORY))

    val fileName = String.format("%s.bzl", name())

    val srcResource = String.format("%s/%s", AspectRepositoryProvider.ASPECT_MODULE_DIRECTORY, fileName)
    val srcStream = this.javaClass.classLoader.getResourceAsStream(srcResource)
      ?: throw IOException("could not module resource file: $srcResource")

    srcStream.use { srcStream ->
      Files.newOutputStream(
        dst.resolve(MODULES_DIRECTORY).resolve(fileName),
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING,
      ).use { dstStream -> srcStream.transferTo(dstStream) }
    }
  }

  @Throws(IOException::class)
  private fun generateDefault(dst: Path) {
    Files.createDirectories(dst.resolve(MODULES_DIRECTORY))

    val fileName = String.format("%s.bzl", name())

    val writer = Files.newOutputStream(
      dst.resolve(MODULES_DIRECTORY).resolve(fileName),
      StandardOpenOption.CREATE,
      StandardOpenOption.TRUNCATE_EXISTING,
    ).bufferedWriter()

    writer.use {
      for (function in functions()) {
        writer.write(String.format(DEFAULT_FUNCTION_TEMPLATE, function.name, function.defaultValue))
      }
    }
  }

  @Throws(SyncFailedException::class)
  final override fun writeDumb(dst: Path, project: Project) {
    try {
      generateDefault(dst)
    } catch (e: IOException) {
      throw SyncFailedException("Could not generate default implementation for module: ${name()}", e)
    }
  }

  @Throws(SyncFailedException::class)
  final override fun write(dst: Path, project: Project, state: SyncProjectState) {
    if (dependencies().any { !it.isFulfilled(state) }) {
      writeDumb(dst, project)
      return
    }

    try {
      copyImplementation(dst)
    } catch (e: IOException) {
      throw SyncFailedException("Could not copy implementation for module: ${name()}", e)
    }
  }
}
