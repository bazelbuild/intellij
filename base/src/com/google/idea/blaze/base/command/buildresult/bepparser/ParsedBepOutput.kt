/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.command.buildresult.bepparser

import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos
import com.google.idea.blaze.common.artifact.OutputArtifact
import org.jetbrains.annotations.TestOnly

/**
 * A data class representing blaze's build event protocol (BEP) output for a build.
 *
 * @property buildId The build identifier
 * @property workspaceStatus URI of the source that the build consumed, if available. The format will be VCS specific.
 * @property fileSets A map from file set ID to file set, with the same ordering as the BEP stream
 * @property buildResult The build exit code
 * @property bepBytesConsumed Number of bytes consumed from BEP stream
 * @property targetsWithErrors The set of build targets that had an error
 * @property configurations Map from configuration ID to configuration details from BEP
 */
data class ParsedBepOutput(
  val buildId: String?,
  val workspaceStatus: ImmutableMap<String, String>,
  val fileSets: ImmutableMap<String, FileSet>,
  val syncStartTimeMillis: Long,
  val buildResult: Int,
  val bepBytesConsumed: Long,
  val targetsWithErrors: ImmutableSet<String>,
  val configurations: ImmutableMap<String, BuildEventStreamProtos.Configuration>
) {
  /**
   * Returns all output artifacts of the build.
   */
  @TestOnly
  fun getAllOutputArtifactsForTesting(): Set<OutputArtifact> {
    return fileSets
      .values
      .flatMap { it.parsedOutputs }
      .toSet()
  }

  /**
   * Returns a map from artifact key to [BepArtifactData] for all artifacts reported during
   * the build.
   */
  fun getFullArtifactData(): ImmutableMap<String, BepArtifactData> {
    return ImmutableMap.copyOf(
      fileSets
        .values
        .flatMap { it.toPerArtifactData() }
        .groupBy { it.artifact.bazelOutRelativePath }
        .mapValues { BepArtifactData.combine(it.value) }
    )
  }

  data class FileSet(
    val parsedOutputs: List<OutputArtifact>,
    val outputGroups: Set<String>,
    val targets: Set<String>
  ) {

    fun toPerArtifactData(): Sequence<BepArtifactData> {
      return parsedOutputs.asSequence().map { BepArtifactData(it, outputGroups, targets) }
    }
  }
}
