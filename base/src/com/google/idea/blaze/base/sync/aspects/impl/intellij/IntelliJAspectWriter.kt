/*
 * Copyright 2026 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.sync.aspects.impl.intellij

import com.google.idea.blaze.base.sync.SyncProjectState
import com.google.idea.blaze.base.sync.SyncScope.SyncFailedException
import com.google.idea.blaze.base.sync.aspects.storage.AspectWriter
import com.intellij.aspect.lib.AspectConfig
import com.intellij.aspect.lib.deployAspectZip
import com.intellij.openapi.project.Project
import java.io.IOException
import java.nio.file.Path

/**
 * Materializes the intellij_aspect_sdk archive into the aspect directory's `intellij` prefix
 * (i.e. `dst`, supplied by [com.google.idea.blaze.base.sync.aspects.storage.AspectStorageService]).
 * The deploy location matches what [IntelliJAspectStrategy] resolves its `--aspects` labels against.
 */
class IntelliJAspectWriter : AspectWriter {

  override fun name(): String = "IntelliJ Aspect (materialized)"

  override fun write(dst: Path, project: Project, state: SyncProjectState) {
    val normalized = dst.toAbsolutePath().normalize()

    val workspaceRoot = state.workspacePathResolver.findWorkspaceRoot(normalized.toFile())
      ?: throw SyncFailedException("could not determine workspace root")

    try {
      deployAspectZip(
          workspaceRoot = workspaceRoot.path(),
          relativeDestination = workspaceRoot.relativize(normalized),
          config = AspectConfig(
              bazelVersion = formatBazelVersion(state),
              repoMapping = emptyMap(),
              useBuiltin = emptySet(),
          ),
          archiveZip = null,
      )
    } catch (e: IOException) {
      throw SyncFailedException("could not deploy the IntelliJ aspect", e)
    }
  }

  private fun formatBazelVersion(state: SyncProjectState): String {
    return state.blazeVersionData.bazelVersion?.toString().orEmpty()
  }
}
