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
package com.google.idea.blaze.cpp

import com.jetbrains.cidr.lang.workspace.OCResolveConfiguration

private const val MARKER = "BAZEL_ID"

/**
 * Wrapper class for the unique identifier derived from a
 */
data class BlazeResolveConfigurationID(
  val identifier: String,
  val configurationId: String,
) {

  companion object {

    @JvmStatic
    fun fromBlazeResolveConfigurationData(data: BlazeResolveConfigurationData): BlazeResolveConfigurationID {
      return BlazeResolveConfigurationID(
        identifier = data.hashCode().toString(),
        configurationId = data.configurationId(),
      )
    }

    @JvmStatic
    fun fromOCResolveConfiguration(config: OCResolveConfiguration): BlazeResolveConfigurationID? {
      val parts = config.uniqueId.split(':')
      if (parts.size != 3) return null

      val (marker, identifier, configurationId) = parts
      if (marker != MARKER) return null

      return BlazeResolveConfigurationID(identifier, configurationId)
    }
  }

  override fun toString(): String {
    return "${MARKER}:${identifier}:${configurationId}"
  }
}