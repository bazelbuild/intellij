/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.sync.importer.problems;

import com.google.common.collect.ImmutableSortedMap;
import com.google.idea.blaze.android.projectview.GeneratedAndroidResourcesSection;
import com.google.idea.blaze.base.async.executor.BlazeExecutor;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** Submits generated resource warnings and potential fixes to the problems view. */
public class GeneratedResourceWarnings {

  private GeneratedResourceWarnings() {}

  public static void submit(
      Consumer<IssueOutput> context,
      Project project,
      ProjectViewSet projectViewSet,
      ArtifactLocationDecoder artifactLocationDecoder,
      Set<ArtifactLocation> generatedResourceLocations,
      Set<String> whitelistedLocations) {
    if (generatedResourceLocations.isEmpty()) {
      return;
    }
    Set<ArtifactLocation> nonWhitelistedLocations = new HashSet<>();
    Set<String> unusedWhitelistEntries = new HashSet<>();
    filterWhitelistedEntries(
        generatedResourceLocations,
        whitelistedLocations,
        nonWhitelistedLocations,
        unusedWhitelistEntries);
    // Tag any warnings with the project view file.
    File projectViewFile = projectViewSet.getTopLevelProjectViewFile().projectViewFile;
    if (!nonWhitelistedLocations.isEmpty()) {
      GeneratedResourceClassifier classifier =
          new GeneratedResourceClassifier(
              project,
              nonWhitelistedLocations,
              artifactLocationDecoder,
              BlazeExecutor.getInstance().getExecutor());
      ImmutableSortedMap<ArtifactLocation, Integer> interestingDirectories =
          classifier.getInterestingDirectories();
      if (!interestingDirectories.isEmpty()) {
        context.accept(IssueOutput.warn(
                String.format(
                    "Dropping %d generated resource directories.\n"
                        + "R classes will not contain resources from these directories.\n"
                        + "Double-click to add to project view if needed to resolve references.",
                    interestingDirectories.size()))
            .inFile(projectViewFile)
            .onLine(1)
            .inColumn(1)
            .build());
        for (Map.Entry<ArtifactLocation, Integer> entry : interestingDirectories.entrySet()) {
          context.accept(IssueOutput.warn(
                  String.format(
                      "Dropping generated resource directory '%s' w/ %d subdirs",
                      entry.getKey(), entry.getValue()))
              .inFile(projectViewFile)
              .navigatable(
                  new AddGeneratedResourceDirectoryNavigatable(
                      project, projectViewFile, entry.getKey()))
              .build());
        }
      }
    }
    // Warn about unused parts of the whitelist.
    if (!unusedWhitelistEntries.isEmpty()) {
      context.accept(IssueOutput.warn(
              String.format(
                  "%d unused entries in project view section \"%s\":\n%s",
                  unusedWhitelistEntries.size(),
                  GeneratedAndroidResourcesSection.KEY.getName(),
                  String.join("\n  ", unusedWhitelistEntries)))
          .inFile(projectViewFile)
          .build());
    }
  }

  private static void filterWhitelistedEntries(
      Set<ArtifactLocation> generatedResourceLocations,
      Set<String> whitelistedLocations,
      Set<ArtifactLocation> nonWhitelistedLocations,
      Set<String> unusedWhitelistEntries) {
    unusedWhitelistEntries.addAll(whitelistedLocations);
    for (ArtifactLocation location : generatedResourceLocations) {
      if (whitelistedLocations.contains(location.getRelativePath())) {
        unusedWhitelistEntries.remove(location.getRelativePath());
      } else {
        nonWhitelistedLocations.add(location);
      }
    }
  }
}
