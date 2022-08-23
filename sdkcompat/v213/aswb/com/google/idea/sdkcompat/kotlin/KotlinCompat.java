package com.google.idea.sdkcompat.kotlin;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.kotlin.cli.common.arguments.Freezable;
import org.jetbrains.kotlin.cli.common.arguments.FreezableKt;
import org.jetbrains.kotlin.idea.configuration.KotlinJavaModuleConfigurator;
import org.jetbrains.kotlin.idea.configuration.NotificationMessageCollector;

/** Provides SDK compatibility shims for Kotlin classes, available to IntelliJ CE & UE. */
public class KotlinCompat {
  private KotlinCompat() {}

  /** #api213 inline in BlazeKotlinSyncPlugin using FreezableKt.unfrozen() */
  public static <T extends Freezable> Freezable unfreezeSettings(T settings) {
    return FreezableKt.unfrozen(settings);
  }

  /**
   * We want to configure only a single module, without a user-facing dialog (the configuration
   * process takes O(seconds) per module, on the EDT, and there can be 100s of modules for Android
   * Studio).
   *
   * <p>The single-module configuration method isn't exposed though, so we need to subclass the
   * configurator.
   *
   * <p>TODO(brendandouglas): remove this hack as soon as there's an appropriate upstream method. *
   *
   * <p>#api213: directly call KotlinJavaModuleConfigurator$configureModule from {@link
   * BlazeKotlinSyncPlugin}
   */
  private static class KotlinLibraryConfigurator extends KotlinJavaModuleConfigurator {
    static final KotlinLibraryConfigurator INSTANCE = new KotlinLibraryConfigurator();

    void configureModule(Project project, Module module) {
      configureModule(
          module,
          getDefaultPathToJarFile(project),
          null,
          new NotificationMessageCollector(project, "Configuring Kotlin", "Configuring Kotlin"));
    }
  }

  /* #api213: directly call KotlinJavaModuleConfigurator$configureModule from {@link
   * BlazeKotlinSyncPlugin}*/
  public static void configureModule(Project project, Module module) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      // do not invoke it later since serviceContainer may be disposed before it get completed
      ApplicationManager.getApplication()
          .invokeAndWait(() -> KotlinLibraryConfigurator.INSTANCE.configureModule(project, module));
    } else {
      ApplicationManager.getApplication()
          .invokeLater(() -> KotlinLibraryConfigurator.INSTANCE.configureModule(project, module));
    }
  }
}
