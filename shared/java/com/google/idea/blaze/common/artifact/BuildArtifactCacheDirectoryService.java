package com.google.idea.blaze.common.artifact;

import com.google.idea.blaze.exception.BuildException;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;

@Service(Service.Level.PROJECT)
public final class BuildArtifactCacheDirectoryService {

  private BuildArtifactCacheDirectory buildArtifactCacheDirectory;

  public <T extends ArtifactFetcher<OutputArtifact>,G extends BuildArtifactCache.CleanRequest> BuildArtifactCacheDirectory getBuildArtifactCache(
      Project project,
      Class<T> artifactFetcherClass,
      Class<G> cleanRequestClass)
      throws BuildException {
    if (buildArtifactCacheDirectory == null) {
      buildArtifactCacheDirectory = new BuildArtifactCacheDirectory(project, artifactFetcherClass, cleanRequestClass);
    }
    return buildArtifactCacheDirectory;
  }
}
