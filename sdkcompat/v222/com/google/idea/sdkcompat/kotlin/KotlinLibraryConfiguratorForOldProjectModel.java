package com.google.idea.sdkcompat.kotlin;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import org.jetbrains.kotlin.idea.configuration.KotlinJavaModuleConfigurator;

/**
 * #api203 The configurator specific for adding kotlin Library to old project model (2020.3 and
 * 2021.1). This is a temporary workaround for b/198439707. TODO(b/198439707): Delete when old
 * project model is not in use anymore.
 */
public class KotlinLibraryConfiguratorForOldProjectModel extends KotlinJavaModuleConfigurator {
  public static final KotlinLibraryConfiguratorForOldProjectModel INSTANCE =
      new KotlinLibraryConfiguratorForOldProjectModel();

  /**
   * Add kotlin library to {@link ModifiableRootModel} when it does not have one. This is similar
   * with {@link KotlinWithLibraryConfigurator#addLibraryToModuleIfNeeded}. But {@link
   * KotlinWithLibraryConfigurator} does not provide a method to update {@link ModifiableRootModel}.
   */
  public void configureModel(
      Project project, ModifiableRootModel modifiableRootModel, Module module) {}
}
