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
package com.google.idea.blaze.typescript;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.io.FileOperationProvider;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.prefetch.PrefetchFileSource;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.sync.projectview.ImportRoots;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Predicate;

/** Declare that ts files should be prefetched. */
public class TypeScriptPrefetchFileSource implements PrefetchFileSource {
  private static final Logger logger = Logger.getInstance(TypeScriptPrefetchFileSource.class);

  private static final BoolExperiment prefetchAllTsSources =
      new BoolExperiment("prefetch.all.ts.sources", true);
  private static final BoolExperiment prefetchTsConfigFilesList =
      new BoolExperiment("prefetch.tsconfig.files.list2", true);

  @Override
  public void addFilesToPrefetch(
      Project project,
      ProjectViewSet projectViewSet,
      ImportRoots importRoots,
      BlazeProjectData blazeProjectData,
      Set<File> files) {
    if (!blazeProjectData
        .getWorkspaceLanguageSettings()
        .isLanguageActive(LanguageClass.TYPESCRIPT)) {
      return;
    }

    if (prefetchAllTsSources.getValue()) {
      // Prefetch all non-project ts source files found during sync
      Predicate<ArtifactLocation> shouldPrefetch =
          location -> {
            if (!location.isSource()) {
              return false;
            }
            WorkspacePath path = WorkspacePath.createIfValid(location.getRelativePath());
            if (path == null || importRoots.containsWorkspacePath(path)) {
              return false;
            }
            String extension = FileUtil.getExtension(path.relativePath());
            return prefetchFileExtensions().contains(extension);
          };
      blazeProjectData.getTargetMap().targets().stream()
          .map(TypeScriptPrefetchFileSource::getJsSources)
          .flatMap(Collection::stream)
          .filter(shouldPrefetch)
          .map(blazeProjectData.getArtifactLocationDecoder()::decode)
          .forEach(files::add);
    }

    if (prefetchTsConfigFilesList.getValue()) {
      // Prefetch all .d.ts files from the tsconfig
      files.addAll(getFilesFromTsConfigs(project, blazeProjectData));
    }
  }

  static Set<File> getFilesFromTsConfigs(Project project, BlazeProjectData projectData) {
    ProjectViewSet projectViewSet = ProjectViewManager.getInstance(project).getProjectViewSet();
    if (projectViewSet == null) {
      return ImmutableSet.of();
    }
    File blazeBin = projectData.getBlazeInfo().getBlazeBinDirectory();
    ImmutableSet.Builder<File> files = ImmutableSet.builder();
    FileOperationProvider fOps = FileOperationProvider.getInstance();
    for (Label label : getTsConfigTargets(projectViewSet)) {
      File directory = new File(blazeBin, label.blazePackage().relativePath());
      File tsconfig = new File(directory, "tsconfig_editor.json");
      JsonObject json;
      try {
        json =
            (JsonObject)
                new JsonParser()
                    .parse(new InputStreamReader(new FileInputStream(tsconfig), Charsets.UTF_8));
      } catch (FileNotFoundException e) {
        continue;
      }
      for (JsonElement fileElement : json.getAsJsonArray("files")) {
        String relativePath = fileElement.getAsString();
        if (relativePath.startsWith("..")) {
          // these are in-source files, ignore them
          continue;
        }
        files.add(maybeResolveSymlink(fOps, new File(directory, relativePath)));
      }
    }
    return files.build();
  }

  private static File maybeResolveSymlink(FileOperationProvider fOps, File file) {
    if (fOps.isSymbolicLink(file)) {
      try {
        return fOps.readSymbolicLink(file);
      } catch (IOException e) {
        logger.warn(e);
      }
    }
    return file;
  }

  private static Set<Label> getTsConfigTargets(ProjectViewSet projectViewSet) {
    Set<Label> labels = new LinkedHashSet<>(projectViewSet.listItems(TsConfigRulesSection.KEY));
    projectViewSet.getScalarValue(TsConfigRuleSection.KEY).ifPresent(labels::add);
    return labels;
  }

  @Override
  public Set<String> prefetchFileExtensions() {
    return getTypeScriptExtensions();
  }

  public static Set<String> getTypeScriptExtensions() {
    return ImmutableSet.of("ts", "tsx");
  }

  private static Collection<ArtifactLocation> getJsSources(TargetIdeInfo target) {
    if (target.getTsIdeInfo() != null) {
      return target.getTsIdeInfo().getSources();
    }
    if (target.getKind().getLanguageClass() == LanguageClass.TYPESCRIPT) {
      return target.getSources();
    }
    return ImmutableList.of();
  }
}
