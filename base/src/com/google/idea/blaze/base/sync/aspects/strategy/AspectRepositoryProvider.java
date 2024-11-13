package com.google.idea.blaze.base.sync.aspects.strategy;

import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.BlazeVersionData;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;

import java.io.File;
import java.nio.file.*;
import java.util.Optional;

public interface AspectRepositoryProvider {
  ExtensionPointName<AspectRepositoryProvider> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.AspectRepositoryProvider");

  static String newRepositoryFlag(boolean useInjectedRepository) {
      if (useInjectedRepository) {
        return "--inject_repository";
      } else {
        return "--override_repository";
      }
  }

  static String overrideRepositoryFlag(boolean useInjectedRepository) {
    return String.format("%s=intellij_aspect", newRepositoryFlag(useInjectedRepository));
  }
  static String overrideRepositoryTemplateFlag(boolean useInjectedRepository) {
    return String.format("%s=intellij_aspect_template", newRepositoryFlag(useInjectedRepository));
  }

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

    Optional<BlazeProjectData> projectData = Optional.ofNullable(
            BlazeProjectDataManager.getInstance(project).getBlazeProjectData());
    boolean useInjectedRepository = projectData
            .map(it -> it.getBlazeVersionData().bazelIsAtLeastVersion(8,0,0))
            .orElse(false); //fall back to false, as override_repository is available for all bazel versions
    return new Optional[] {
      getOverrideFlagForAspectDirectory(useInjectedRepository),
      getOverrideFlagForProjectAspectDirectory(project, useInjectedRepository),
    };
  }

  private static Optional<String> getOverrideFlagForAspectDirectory(boolean useInjectedRepository) {
    return findAspectDirectory().map(it -> overrideRepositoryFlag(useInjectedRepository) + "=" + it.getPath());
  }

  private static Optional<String> getOverrideFlagForProjectAspectDirectory(Project project, boolean useInjectedRepository) {
    return getProjectAspectDirectory(project).map(it -> overrideRepositoryTemplateFlag(useInjectedRepository) + "=" + it.getPath());
  }
}
