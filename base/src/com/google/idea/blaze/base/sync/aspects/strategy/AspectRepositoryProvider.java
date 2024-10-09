package com.google.idea.blaze.base.sync.aspects.strategy;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

public interface AspectRepositoryProvider {
  ExtensionPointName<AspectRepositoryProvider> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.AspectRepositoryProvider");

  String OVERRIDE_REPOSITORY_FLAG = "--override_repository=intellij_aspect";
  String OVERRIDE_REPOSITORY_TEMPLATE_FLAG = "--override_repository=intellij_aspect_template";

  Optional<File> aspectDirectory();

  default Optional<File> aspectTemplateDirectory() {
    return Optional.empty();
  }

  public static Optional<File> getProjectAspectDirectory(Project project) {
    String basePath = project.getBasePath();
    return basePath == null ? Optional.empty() : Optional.of(Paths.get(basePath).resolve("aspect").toFile());
  }

  private static Optional<File> findAspectDirectory() {
    return EP_NAME.getExtensionsIfPointIsRegistered().stream()
        .map(AspectRepositoryProvider::aspectDirectory)
        .filter(Optional::isPresent)
        .findFirst()
        .orElse(Optional.empty());
  }

  public static Optional<File> findAspectTemplateDirectory() {
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

  public static Optional<String> getOverrideFlagForAspectDirectory() {
    return findAspectDirectory().map(it -> OVERRIDE_REPOSITORY_FLAG + "=" + it.getPath());
  }

  private static Optional<String> getOverrideFlagForProjectAspectDirectory(Project project) {
    return getProjectAspectDirectory(project).map(it -> OVERRIDE_REPOSITORY_TEMPLATE_FLAG + "=" + it.getPath());
  }

  static void copyAspectTemplatesIfNotExists(Project project) throws ExecutionException {
    Path destinationAspectsPath = getProjectAspectDirectory(project).map(File::toPath).orElse(null);
    if (destinationAspectsPath == null) {
      throw new IllegalStateException("Missing project aspect directory");
    }
    if (!destinationAspectsPath.toFile().exists()) {
      try {
        copyAspectTemplatesFromResources(destinationAspectsPath);
      } catch (IOException e) {
        throw new ExecutionException(e);
      }
    }
  }

  private static void copyAspectTemplatesFromResources(Path destinationPath) throws IOException {
    Path aspectPath = findAspectTemplateDirectory().map(File::toPath).orElse(null);
    if (aspectPath != null && Files.isDirectory(aspectPath)) {
        copyFileTree(aspectPath, destinationPath);
    } else {
      System.out.println("Missing aspects resource");
    }
  }

  private static void copyFileTree(Path source, Path destination) throws IOException {
    Stream<Path> paths = Files.walk(source);
      paths.forEach(path -> {
          try {
              copyUsingRelativePath(source, path, destination);
          } catch (IOException e) {
              throw new RuntimeException(e);
          }
      });
  }

  private static void copyUsingRelativePath(Path sourcePrefix, Path source, Path destination) throws IOException {
    // only interested in bzl files that are templates
    if (source.endsWith(".bzl") && !source.endsWith("template.bzl")) return;
    String sourceRelativePath = sourcePrefix.relativize(source).toString();
    Path destinationAbsolutePath = Paths.get(destination.toString(), sourceRelativePath);
    Files.copy(source, destinationAbsolutePath);
  }
}
