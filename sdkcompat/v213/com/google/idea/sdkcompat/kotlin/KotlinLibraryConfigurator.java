package com.google.idea.sdkcompat.kotlin;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.kotlin.idea.configuration.KotlinJavaModuleConfigurator;
import org.jetbrains.kotlin.idea.configuration.NotificationMessageCollector;

/**
 * We want to configure only a single module, without a user-facing dialog (the configuration
 * process takes O(seconds) per module, on the EDT, and there can be 100s of modules for Android
 * Studio).
 *
 * <p>The single-module configuration method isn't exposed though, so we need to subclass the
 * configurator.
 *
 * <p>TODO(brendandouglas): remove this hack as soon as there's an appropriate upstream method.
 *
 * <p>#api213: move class to BlazeKotlinSyncPlugin and call configureModule(Project,
 * NotificationMessageCollector)
 */
public class KotlinLibraryConfigurator extends KotlinJavaModuleConfigurator {
  public static final KotlinLibraryConfigurator INSTANCE = new KotlinLibraryConfigurator();

  public void configureModule(Project project, Module module) {
    configureModule(
        module,
        getDefaultPathToJarFile(project),
        /*pathFromDialog=*/ null,
        new NotificationMessageCollector(project, "Configuring Kotlin", "Configuring Kotlin"));
  }
}
