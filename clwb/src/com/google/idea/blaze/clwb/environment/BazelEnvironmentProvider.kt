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
package com.google.idea.blaze.clwb.environment

import com.google.idea.blaze.cpp.BlazeCompilerSettings
import com.google.idea.blaze.cpp.CppEnvironmentProvider
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.jetbrains.cidr.cpp.toolchains.CPPEnvironment
import com.jetbrains.cidr.cpp.toolchains.CPPToolchains
import com.jetbrains.cidr.lang.toolchains.CidrToolEnvironment

/**
 * Fallback environment provider. Forwards the environment variables set on the
 * Bazel compiler settings.
 */
class BazelEnvironmentProvider : CppEnvironmentProvider {

  companion object {

    /**
     * Creates a CPPEnvironment that forwards the environment variables set on
     * the Bazel compiler settings.
     */
    @JvmStatic
    fun create(settings: BlazeCompilerSettings, toolchain: CPPToolchains.Toolchain): CPPEnvironment {
      return object : CPPEnvironment(toolchain) {
        @Throws(ExecutionException::class)
        override fun prepare(cl: GeneralCommandLine, prepareFor: PrepareFor) {
          super.prepare(cl, prepareFor)
          cl.environment.putAll(settings.environment())
        }
      }
    }

  }

  /**
   * Creates a CidrToolEnvironment that forwards the environment variables set
   * on the Bazel compiler settings.
   */
  override fun create(settings: BlazeCompilerSettings): CidrToolEnvironment = object : CidrToolEnvironment() {
    @Throws(ExecutionException::class)
    override fun prepare(cl: GeneralCommandLine, prepareFor: PrepareFor) {
      super.prepare(cl, prepareFor)
      cl.environment.putAll(settings.environment())
    }
  }
}