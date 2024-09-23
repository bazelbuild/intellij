/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.command.buildresult

import com.google.common.collect.ImmutableSet
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos.BuildEvent
import com.google.idea.blaze.base.command.buildresult.BuildEventStreamProvider.BuildEventStreamException
import com.google.idea.blaze.base.model.primitives.Label
import com.google.idea.blaze.base.run.testlogs.BlazeTestResults
import com.google.idea.blaze.common.artifact.OutputArtifact
import com.google.idea.blaze.exception.BuildException
import com.intellij.openapi.diagnostic.Logger
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.*
import java.util.function.Predicate

private val LOG = Logger.getInstance(BuildResultHelper::class.java)

/**
 * Build event protocol implementation to get build results.
 *
 * The build even protocol (BEP for short) is a proto-based protocol used by bazel to communicate
 * build events.
 */
class BuildResultHelper(val outputFile: File) : AutoCloseable {

  constructor() : this(BuildEventProtocolUtils.createTempOutputFile())

  /**
   * Returns the build flags necessary for the build result helper to work.
   *
   * <p>The user must add these flags to their build command.
   */
  fun getBuildFlags(): List<String> = BuildEventProtocolUtils.getBuildFlags(outputFile)

  /**
   * Parses the BEP output data and returns the corresponding {@link ParsedBepOutput}.
   */
  @Throws(GetArtifactsException::class)
  fun getBuildOutput(): ParsedBepOutput {
    return try {
      BufferedInputStream(FileInputStream(outputFile)).use(ParsedBepOutput::parseBepArtifacts)
    } catch (e: IOException) {
      LOG.error(e)
      throw GetArtifactsException(e.message)
    } catch (e: BuildEventStreamException) {
      LOG.error(e)
      throw GetArtifactsException(e.message)
    }
  }

  /**
   * Parses the BEP output data and returns the corresponding {@link ParsedBepOutput}.
   */
  fun getTestResults(): BlazeTestResults {
    return try {
      BufferedInputStream(FileInputStream(outputFile)).use(BuildEventProtocolOutputReader::parseTestResults)
    } catch (e: IOException) {
      LOG.warn(e)
      return BlazeTestResults.NO_RESULTS
    } catch (e: BuildEventStreamException) {
      LOG.warn(e)
      return BlazeTestResults.NO_RESULTS
    }
  }

  fun deleteTemporaryOutputFiles() {
    outputFile.delete()
  }

  @Throws(GetFlagsException::class)
  fun getBlazeFlags(): BuildFlags {
    return try {
      BufferedInputStream(FileInputStream(outputFile)).use(BuildFlags::parseBep)
    } catch (e: IOException) {
      throw GetFlagsException(e)
    } catch (e: BuildEventStreamException) {
      throw GetFlagsException(e)
    }
  }

  /**
   * Returns the build artifacts, filtering out all artifacts not directly produced by the specified
   * target.
   */
  @Throws(GetArtifactsException::class)
  fun getBuildArtifactsForTarget(
    target: Label,
    pathFilter: Predicate<String>,
  ): ImmutableSet<OutputArtifact> {
    return getBuildOutput().getDirectArtifactsForTarget(target, pathFilter)
  }

  override fun close() {
    deleteTemporaryOutputFiles()
  }

  class GetArtifactsException : BuildException {
    constructor(cause: Throwable?) : super(cause)

    constructor(message: String?) : super(message)

    constructor(message: String?, cause: Throwable?) : super(message, cause)
  }

  class GetFlagsException(cause: Throwable?) : Exception(cause)
}
