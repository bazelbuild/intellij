package com.google.idea.testing.bazel

import com.google.devtools.build.runfiles.Runfiles
import com.google.idea.testing.bazel.BazelProjectFixtureProto.ProjectFixtureConfig
import com.google.protobuf.TextFormat
import org.junit.rules.ExternalResource
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.nio.file.Files
import java.nio.file.Path

private val RUNFILES = Runfiles.preload()

private const val ENVIRONMENT_VARIABLE_NAME = "BAZEL_TEST_CONFIG"

class BazelProjectFixture : ExternalResource() {

  lateinit var projectDirectory: Path private set
  lateinit var bazelExecutable: Path private set
  lateinit var repositoryCache: Path private set
  lateinit var bazelVersion: String private set

  override fun apply(base: Statement, description: Description): Statement {
    val config = parseConfig()

    projectDirectory = Files.createTempDirectory("bazel-fixture-project")
    unzip(config.projectArchive.rlocation(), projectDirectory)

    repositoryCache = Files.createTempDirectory("bazel-fixture-cache")
    unzip(config.repoCacheArchive.rlocation(), repositoryCache)

    bazelExecutable = config.bazelBinary.executable.rlocation()
    bazelVersion = config.bazelBinary.version

    return super.apply(base, description)
  }
}

private fun parseConfig(): ProjectFixtureConfig {
  val file = System.getenv(ENVIRONMENT_VARIABLE_NAME) ?: System.getProperty(ENVIRONMENT_VARIABLE_NAME)
    ?: throw IllegalStateException("Missing $ENVIRONMENT_VARIABLE_NAME environment variable")

  val config = Files.readString(file.rlocation())

  return ProjectFixtureConfig.newBuilder().also {
    TextFormat.Parser.newBuilder().build().merge(config, it)
  }.build()
}

private fun String.rlocation(): Path = Path.of(RUNFILES.unmapped().rlocation(this))