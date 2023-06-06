package com.google.idea.sdkcompat.kotlin;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import java.util.ArrayList;
import java.util.List;
import kotlin.Unit;
import kotlin.jvm.functions.Function0;
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

  /* #api213: inline in {@link BlazeKotlinSyncPlugin}*/
  public static void configureModule(Project project, Module module) {
    KotlinJavaModuleConfigurator configurator =
        KotlinJavaModuleConfigurator.Companion.getInstance();
    NotificationMessageCollector collector =
        new NotificationMessageCollector(project, "Configuring Kotlin", "Configuring Kotlin");
    Application application = ApplicationManager.getApplication();

    application.invokeAndWait(
        () -> {
          configurator.getOrCreateKotlinLibrary(project, collector);
        });

    List<Function0<Unit>> writeActions = new ArrayList<>();
    application.runReadAction(
        () -> {
          configurator.configureModule(module, collector, writeActions);
        });

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      // do not invoke it later since serviceContainer may be disposed before it get completed
      application.invokeAndWait(
          () -> {
            writeActions.stream().forEach(Function0::invoke);
          });
    } else {
      application.invokeLater(
          () -> {
            writeActions.stream().forEach(Function0::invoke);
          });
    }
  }
}
