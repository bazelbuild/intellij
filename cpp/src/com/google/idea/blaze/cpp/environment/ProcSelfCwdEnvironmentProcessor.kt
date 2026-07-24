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
package com.google.idea.blaze.cpp.environment

import com.google.idea.blaze.base.model.primitives.ExecutionRootPath
import com.google.idea.blaze.base.sync.workspace.ExecutionRootPathResolver

/**
 * Rewrites `/proc/self/cwd/` environment values to absolute paths.
 */
class ProcSelfCwdEnvironmentProcessor : EnvironmentProcessor.Transform() {

    override fun enabled(): Boolean {
        return true
    }

    override fun apply(key: String, value: String, resolver: ExecutionRootPathResolver): String? {
        if (!ExecutionRootPath.isProcSelfCwd(value)) {
            return null
        }

        return resolver.resolveExecutionRootPath(ExecutionRootPath.create(value))?.absolutePath
    }
}