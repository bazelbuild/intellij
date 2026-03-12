package com.google.idea.testing.bazel

import com.google.idea.testing.bazel.BazelCacheBuilderProto.BuilderInput
import com.google.protobuf.TextFormat
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * Builds a repository cache by running `bazel build --nobuild` with each configured Bazel version. The populated cache
 * directory is then archived for reuse by integration tests.
 */
fun main(args: Array<String>) {
  val input = parseInput(args)

  val projectDirectory = tempDirectory("project")
  unzip(Path.of(input.project), projectDirectory)

  val cacheDirectory = tempDirectory("cache")
  val outputRootDirectory = tempDirectory("output")

  for (bazel in input.bazelBinariesList) {
    val cmd = mutableListOf<String>()
    cmd.add(Path.of(bazel.executable).toAbsolutePath().toString())
    cmd.add("--output_user_root=$outputRootDirectory")
    cmd.add("build")
    cmd.add("--nobuild")
    cmd.add("--repository_cache=$cacheDirectory")

    cmd.addAll(input.flagsList)
    cmd.addAll(input.targetsList)

    execute(cmd, projectDirectory)
  }

  zip(cacheDirectory, Path.of(input.output))
}

private fun parseInput(args: Array<String>): BuilderInput {
  require(args.size == 1)

  return BuilderInput.newBuilder().also {
    TextFormat.Parser.newBuilder().build().merge(args[0], it)
  }.build()
}

@Throws(IOException::class)
private fun execute(cmd: List<String>, cwd: Path) {
  val process = ProcessBuilder(cmd)
    .directory(cwd.toFile())
    .redirectErrorStream(true)
    .start()

  if (process.waitFor() != 0) {
    process.inputStream.transferTo(System.err)
    throw IOException("Command failed: ${cmd.joinToString(" ")}")
  }
}
