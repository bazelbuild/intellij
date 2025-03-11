package com.google.idea.blaze.common.artifact;

import com.google.idea.blaze.exception.BuildException;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;

@Service(Service.Level.PROJECT)
public final class BuildArtifactCacheDirectoryService {

  private BuildArtifactCacheDirectory buildArtifactCacheDirectory;

  public BuildArtifactCacheDirectory getBuildArtifactCache(Project project) throws BuildException {
    if (buildArtifactCacheDirectory == null) {
      buildArtifactCacheDirectory = new BuildArtifactCacheDirectory(project);
    }
    return buildArtifactCacheDirectory;
  }
}
