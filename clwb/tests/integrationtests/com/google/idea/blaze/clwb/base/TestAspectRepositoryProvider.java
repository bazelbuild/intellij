package com.google.idea.blaze.clwb.base;

import com.google.idea.blaze.base.sync.aspects.strategy.AspectRepositoryProvider;
import com.google.idea.testing.runfiles.Runfiles;
import com.intellij.openapi.project.Project;

import java.io.File;
import java.nio.file.Path;
import java.util.Optional;

public class TestAspectRepositoryProvider implements AspectRepositoryProvider {

  @Override
  public Optional<File> aspectDirectory() {
    return Optional.of(Runfiles.runfilesPath("aspect")).map(Path::toFile);
  }

  static Optional<String>[] getOverrideFlags(Project project) {
    return new Optional[] {
      AspectRepositoryProvider.getOverrideFlagForAspectDirectory(),
      getOverrideFlagForAspectTemplateDirectory(project),
    };
  }

  private static Optional<String> getOverrideFlagForAspectTemplateDirectory(Project project) {
    return AspectRepositoryProvider.findAspectTemplateDirectory().map(it -> OVERRIDE_REPOSITORY_TEMPLATE_FLAG + "=" + it.getPath());
  }

  @Override
  public Optional<File> aspectTemplateDirectory() {
    return Optional.of(Runfiles.runfilesPath("aspect_template")).map(Path::toFile);
  }
}
