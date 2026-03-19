package com.google.idea.blaze.base.buildview

import com.google.idea.blaze.base.bazel.BazelExitCodeException
import com.google.idea.blaze.exception.BuildException
import com.intellij.openapi.diagnostic.logger
import okio.IOException
import java.io.BufferedInputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path

private val LOG = logger<ExecResult>()

/**
 * Result of a non-build Bazel command execution.
 *
 * This object is [AutoCloseable] and must be closed to clean up the temporary file holding stdout.
 * Use [stdout] for streaming access, [readAsString] for convenience, and [throwOnFailure] to check
 * the exit code.
 */
class ExecResult(
  @JvmField val exitCode: Int,
  private val tempFile: Path,
) : AutoCloseable {

  /** Returns a new [InputStream] over the stdout output. Each call opens a fresh stream. */
  val stdout: InputStream get() = BufferedInputStream(Files.newInputStream(tempFile))

  /** Reads the entire stdout output as a UTF-8 string. */
  fun readAsString(): String = Files.readString(tempFile)

  /**
   * Throws [BuildException] if the exit code indicates failure (non-zero).
   */
  @Throws(BuildException::class)
  fun throwOnFailure() {
    BazelExitCodeException.throwIfFailed("exec", exitCode)
  }

  /**
   * Throws [BuildException] if the exit code indicates failure, respecting the given [options].
   * For example, [BazelExitCodeException.ThrowOption.ALLOW_PARTIAL_SUCCESS] permits exit code 3.
   */
  @Throws(BuildException::class)
  fun throwOnFailure(vararg options: BazelExitCodeException.ThrowOption) {
    BazelExitCodeException.throwIfFailed("exec", exitCode, *options)
  }

  override fun close() {
    try {
      Files.deleteIfExists(tempFile)
    } catch (e: IOException) {
      // best effort cleanup
      LOG.warn("could not delete temp output file", e)
    }
  }
}
