package com.google.idea.blaze.base.sync.aspects.strategy;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;

import java.io.File;
import java.nio.file.*;
import java.util.Optional;

public interface AspectRepositoryProvider {
  ExtensionPointName<AspectRepositoryProvider> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.AspectRepositoryProvider");

  String OVERRIDE_REPOSITORY_FLAG = "--override_repository=intellij_aspect";
  String OVERRIDE_REPOSITORY_TEMPLATE_FLAG = "--override_repository=intellij_aspect_template";

  Optional<File> aspectDirectory();

  default Optional<File> aspectTemplateDirectory() {
    return Optional.empty();
  }

  static Optional<File> getProjectAspectDirectory(Project project) {
    return Optional.ofNullable(project.getBasePath()).map((it) -> Paths.get(it).resolve("aspect").toFile());
  }

  private static Optional<File> findAspectDirectory() {
    return EP_NAME.getExtensionsIfPointIsRegistered().stream()
        .map(AspectRepositoryProvider::aspectDirectory)
        .filter(Optional::isPresent)
        .findFirst()
        .orElse(Optional.empty());
  }

  static Optional<File> findAspectTemplateDirectory() {
    return EP_NAME.getExtensionsIfPointIsRegistered().stream()
            .map(AspectRepositoryProvider::aspectTemplateDirectory)
            .filter(Optional::isPresent)
            .findFirst()
            .orElse(Optional.empty());
  }

  static Optional<String>[] getOverrideFlags(Project project) {
    return new Optional[] {
      getOverrideFlagForAspectDirectory(),
      getOverrideFlagForProjectAspectDirectory(project),
    };
  }

  private static Optional<String> getOverrideFlagForAspectDirectory() {
    return findAspectDirectory().map(it -> OVERRIDE_REPOSITORY_FLAG + "=" + it.getPath());
  }

  private static Optional<String> getOverrideFlagForProjectAspectDirectory(Project project) {
    return getProjectAspectDirectory(project).map(it -> OVERRIDE_REPOSITORY_TEMPLATE_FLAG + "=" + it.getPath());
  }
}
