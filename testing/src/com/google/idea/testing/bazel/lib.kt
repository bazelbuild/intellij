package com.google.idea.testing.bazel

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.Collections
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.jvm.Throws

private val tempDirectories by lazy {
  Collections.synchronizedList(mutableListOf<Path>()).also {
    Runtime.getRuntime().addShutdownHook(Thread { deleteDirectories(it) })
  }
}

@Throws(IOException::class)
fun tempDirectory(name: String): Path {
  // this is either a sandbox relative path or to the execroot if no sandbox is used
  val directory = Files.createTempDirectory(Path.of("."), name).toAbsolutePath().normalize()
  tempDirectories.add(directory)

  return directory
}

@OptIn(ExperimentalPathApi::class)
private fun deleteDirectories(directories: List<Path>) {
  for (directory in directories) {
    try {
      directory.deleteRecursively()
    } catch (_: IOException) {
      // best effort cleanup during shutdown
    }
  }
}