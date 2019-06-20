/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.javascript;


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

/** Declare that js files should be prefetched. */
public class JavascriptPrefetchFileSource implements PrefetchFileSource, OutputsProvider {

  @Override
  public boolean isActive(WorkspaceLanguageSettings languageSettings) {
    return languageActive(languageSettings);
  }

  @Override
  public Collection<ArtifactLocation> selectAllRelevantOutputs(TargetIdeInfo target) {
    return getJsSources(target);
  }

  @Override
  public void addFilesToPrefetch(
      Project project,
      ProjectViewSet projectViewSet,
      ImportRoots importRoots,
      BlazeProjectData blazeProjectData,
      Set<File> files) {
    files.addAll(
        BlazeJavascriptAdditionalLibraryRootsProvider.getLibraryFiles(
            project, blazeProjectData, importRoots));
  }

  @Override
  public Set<String> prefetchFileExtensions() {
    return getJavascriptExtensions();
  }

  static Set<String> getJavascriptExtensions() {
    return ImmutableSet.of("js", "html", "css", "gss");
  }

  private static Collection<ArtifactLocation> getJsSources(TargetIdeInfo target) {
    if (target.getJsIdeInfo() != null) {
      return target.getJsIdeInfo().getSources();
    }
    if (target.getKind().getLanguageClass() == LanguageClass.JAVASCRIPT) {
      return target.getSources();
    }
    return ImmutableList.of();
  }

  static boolean languageActive(WorkspaceLanguageSettings settings) {
    return settings.isLanguageActive(LanguageClass.JAVASCRIPT)
        || settings.isLanguageActive(LanguageClass.TYPESCRIPT);
  }
}
