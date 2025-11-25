package com.google.idea.datafiles

import com.intellij.util.PathUtil
import java.nio.file.Path

object Datafiles {

  private val TEST_SRCDIR by lazy { System.getenv("TEST_SRCDIR") }

  private val TEST_WORKSPACE by lazy { System.getenv("TEST_WORKSPACE") }

  /**
   * Load data files from runfiles during testing.
   */
  val fromRunfiles get() = TEST_SRCDIR != null && TEST_WORKSPACE != null

  /**
   * Load data files from the plugin directory in production.
   */
  val fromPluginDir get() = !fromRunfiles

  /**
   * Resolve a data file path relative to the correct location for the current environment.
   */
  fun resolve(relativePath: String): Path {
    return if (fromRunfiles) {
      Path.of(TEST_SRCDIR, TEST_WORKSPACE, relativePath)
    } else {
      val jarPath = Path.of(PathUtil.getJarPathForClass(Datafiles::class.java))
      // go two up from the plugin jar to find the plugin's root directory
      jarPath.parent.parent.resolve(relativePath)
    }
  }

  fun resolveLazy(relativePath: String): Lazy<Path> {
    return lazy { resolve(relativePath) }
  }
}