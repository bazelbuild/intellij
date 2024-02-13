package com.google.idea.blaze.android.projectsystem;

import com.android.ide.common.repository.GradleCoordinate;
import com.google.common.annotations.VisibleForTesting;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.settings.BuildSystemName;

public class DefaultMavenArtifactLocator implements MavenArtifactLocator {
  private final BuildSystemName buildSystemName;

  public DefaultMavenArtifactLocator() {
    this.buildSystemName = BuildSystemName.Bazel;
  }

  @VisibleForTesting
  public DefaultMavenArtifactLocator(BuildSystemName buildSystemName) {
    this.buildSystemName = buildSystemName;
  }

  public Label labelFor(GradleCoordinate coordinate) {
    return Label.create(String.format("@maven//:%s_%s",
            coordinate.getGroupId().replaceAll("[.-]", "_"),
            coordinate.getArtifactId().replaceAll("[.-]", "_")
        )
    );
  }

  public BuildSystemName buildSystem() {
    return buildSystemName;
  }
}