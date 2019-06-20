/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.golang.sync;


import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.OutputsProvider;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.prefetch.PrefetchFileSource;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.sync.projectview.ImportRoots;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.util.Collection;
import java.util.Set;

/** Declare that go files should be prefetched. */
public class GoPrefetchFileSource implements PrefetchFileSource, OutputsProvider {

  @Override
  public boolean isActive(WorkspaceLanguageSettings languageSettings) {
    return languageSettings.isLanguageActive(LanguageClass.GO);
  }

  @Override
  public Collection<ArtifactLocation> selectAllRelevantOutputs(TargetIdeInfo target) {
    return target.getGoIdeInfo() != null ? target.getGoIdeInfo().getSources() : ImmutableList.of();
  }

  @Override
  public void addFilesToPrefetch(
      Project project,
      ProjectViewSet projectViewSet,
      ImportRoots importRoots,
      BlazeProjectData blazeProjectData,
      Set<File> files) {
    files.addAll(
        BlazeGoAdditionalLibraryRootsProvider.getLibraryFiles(
            project, blazeProjectData, importRoots));
  }

  @Override
  public Set<String> prefetchFileExtensions() {
    return ImmutableSet.of("go");
  }
}
