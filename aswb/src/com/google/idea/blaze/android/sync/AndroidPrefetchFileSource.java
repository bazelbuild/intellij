package com.google.idea.blaze.android.sync;

import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.prefetch.PrefetchFileSource;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.sync.projectview.ImportRoots;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.util.Set;

public class AndroidPrefetchFileSource implements PrefetchFileSource {

  @Override
  public void addFilesToPrefetch(
      Project project,
      ProjectViewSet projectViewSet,
      ImportRoots importRoots,
      BlazeProjectData blazeProjectData,
      Set<File> files) {}

  @Override
  public Set<String> prefetchFileExtensions() {
    return ImmutableSet.of("xml");
  }
}
