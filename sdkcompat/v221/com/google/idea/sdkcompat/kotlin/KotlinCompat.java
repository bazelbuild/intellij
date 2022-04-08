package com.google.idea.sdkcompat.kotlin;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.Library;
import org.jetbrains.kotlin.idea.configuration.KotlinWithLibraryConfigurator;
import org.jetbrains.kotlin.idea.configuration.NotificationMessageCollector;

/** Provides SDK compatibility shims for Kotlin classes, available to IntelliJ CE & UE. */
public class KotlinCompat {
  private KotlinCompat() {}

  /**
   * #api213: KotlinWithLibraryConfigurator$getKotlinLibrary(Project) is made private in IJ 2022.1.
   * We can use KotlinWithLibraryConfigurator$getOrCreateKotlinLibrary instead.
   */
  public static Library getOrCreateKotlinLibrary(
      KotlinWithLibraryConfigurator configurator,
      Project project,
      NotificationMessageCollector collector) {
    return configurator.getOrCreateKotlinLibrary(project, collector);
  }
}
