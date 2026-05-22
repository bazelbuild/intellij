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

import com.google.common.collect.ImmutableMap
import com.google.idea.blaze.cpp.BlazeCompilerSettings
import com.google.idea.blaze.cpp.CppEnvironmentProvider
import com.intellij.execution.configurations.GeneralCommandLine
import com.jetbrains.cidr.lang.toolchains.CidrToolEnvironment

/**
 * Forwards environment variables from [BlazeCompilerSettings.environment] to compiler invocations.
 * 
 * Previously, these environment variables (e.g. `DEVELOPER_DIR`, `SDKROOT` on macOS)
 * were embedded as `export` statements in the compiler wrapper script. Now that the wrapper
 * script has been removed, this provider ensures they are still passed to the compiler.
 * 
 * This provider is registered with `order="last"` so that more specific providers
 * (MSVC, Clang-CL) take precedence when applicable.
 */
class BazelEnvironmentProvider : CppEnvironmentProvider {

  override fun create(settings: BlazeCompilerSettings): CidrToolEnvironment? {
    val env = settings.environment()
    if (env.isEmpty()) return null

    return ToolEnvironment(env)
  }

  private class ToolEnvironment(private val environment: ImmutableMap<String, String>) : CidrToolEnvironment() {

    override fun prepare(cl: GeneralCommandLine, prepareFor: PrepareFor) {
      cl.environment.putAll(environment)
    }
  }
}
