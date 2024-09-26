package com.google.idea.blaze.base.sync.aspects.strategy;

import com.intellij.openapi.extensions.ExtensionPointName;
import java.io.File;
import java.util.Optional;

public interface AspectRepositoryProvider {
  ExtensionPointName<AspectRepositoryProvider> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.AspectRepositoryProvider");

  String OVERRIDE_REPOSITORY_FLAG = "--override_repository=intellij_aspect";

  Optional<File> aspectDirectory();

  static Optional<File> findAspectDirectory() {
    return EP_NAME.getExtensionsIfPointIsRegistered().stream()
        .map(AspectRepositoryProvider::aspectDirectory)
        .filter(Optional::isPresent)
        .findFirst()
        .orElse(Optional.empty());
  }

  static Optional<String> getOverrideFlag() {
    return findAspectDirectory().map(it -> OVERRIDE_REPOSITORY_FLAG + "=" + it.getPath());
  }
}
