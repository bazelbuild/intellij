package com.google.idea.blaze.base.sync.aspects.strategy;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;

import java.io.File;
import java.util.Optional;

public interface AspectRepositoryProvider {
  ExtensionPointName<AspectRepositoryProvider> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.AspectRepositoryProvider");

  String OVERRIDE_REPOSITORY_FLAG = "--override_repository=intellij_aspect";

  Optional<File> aspectDirectory(Project project);

  static Optional<File> findAspectDirectory(Project project) {
    return EP_NAME.getExtensionsIfPointIsRegistered().stream()
        .map(aspectRepositoryProvider -> aspectRepositoryProvider.aspectDirectory(project))
        .filter(Optional::isPresent)
        .findFirst()
        .orElse(Optional.empty());
  }

  static Optional<String> getOverrideFlag(Project project) {
    return findAspectDirectory(project).map(it -> OVERRIDE_REPOSITORY_FLAG + "=" + it.getPath());
  }
}
