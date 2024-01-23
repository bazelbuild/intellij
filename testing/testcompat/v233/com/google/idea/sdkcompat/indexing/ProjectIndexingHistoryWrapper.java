/*
 * Copyright 2021 The Bazel Authors. All rights reserved.
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
package com.google.idea.sdkcompat.indexing;

import com.google.common.collect.ImmutableList;
import com.intellij.openapi.project.Project;
import com.intellij.util.indexing.diagnostic.IndexingFileSetStatistics;
import com.intellij.util.indexing.diagnostic.ProjectDumbIndexingHistory;
import com.intellij.util.indexing.diagnostic.ProjectDumbIndexingHistoryImpl;
import com.intellij.util.indexing.diagnostic.dto.JsonDuration;
import com.intellij.util.indexing.diagnostic.dto.JsonFileProviderIndexStatistics;
import java.time.Duration;

/** #api212: inline into IndexingLoggerTest */
@SuppressWarnings("UnstableApiUsage")
public class ProjectIndexingHistoryWrapper {
  private final ProjectDumbIndexingHistory projectIndexingHistory;

  private ProjectIndexingHistoryWrapper(ProjectDumbIndexingHistory projectIndexingHistory) {
    this.projectIndexingHistory = projectIndexingHistory;
  }

  public static ProjectIndexingHistoryWrapper create(Project project) {
    return create(project, /* providerName= */ "");
  }

  public static ProjectIndexingHistoryWrapper create(Project project, String providerName) {
    return create(project, providerName, /* providerIndexingTime= */ Duration.ZERO);
  }

  public static ProjectIndexingHistoryWrapper create(
      Project project, String providerName, Duration providerIndexingTime) {
    return create(
        project,
        /* totalIndexingTime= */ Duration.ZERO,
        /* scanFilesDuration= */ Duration.ZERO,
        /* totalUpdatingTime= */ Duration.ZERO,
        providerName,
        /* providerIndexingTime= */ providerIndexingTime);
  }

  public static ProjectIndexingHistoryWrapper create(
      Project project,
      Duration totalIndexingTime,
      Duration scanFilesDuration,
      Duration totalUpdatingTime,
      String providerName,
      Duration providerIndexingTime) {
    ImmutableList<JsonFileProviderIndexStatistics> provider;
    if (providerName.isEmpty()) {
      provider = ImmutableList.of();
    } else {
      provider =
          ImmutableList.of(
              new JsonFileProviderIndexStatistics(
                  providerName,
                  /* totalNumberOfIndexedFiles= */ 0,
                  /* totalNumberOfFilesFullyIndexedByExtensions= */ 0,
                  /* totalNumberOfNothingToWriteFiles= */ 0,
                  /* totalIndexingVisibleTime= */ new JsonDuration(
                      /* nano= */ providerIndexingTime.toNanos()),
                  /* contentLoadingVisibleTime= */ new JsonDuration(/* nano= */ 0),
                  /* numberOfTooLargeForIndexingFiles= */ 0,
                  /* slowIndexedFiles= */ ImmutableList.of(),
                  /* filesFullyIndexedByExtensions= */ ImmutableList.of(),
                  /* separateApplyingIndexesVisibleTime= */ new JsonDuration(/* nano= */ 0),
                  /* indexedFiles= */ ImmutableList.of()));
    }

    var history = new ProjectDumbIndexingHistoryImpl(project);
    if (!providerName.isEmpty()) {
      history.setVisibleTimeToAllThreadsTimeRatio(1.0);
      var stats = new IndexingFileSetStatistics(project, providerName);
      stats.setProcessingTimeInAllThreads(providerIndexingTime.toNanos());
      history.addProviderStatistics(stats);
    }
    return new ProjectIndexingHistoryWrapper(history);
  }

  public ProjectDumbIndexingHistory getProjectIndexingHistory() {
    return projectIndexingHistory;
  }
}
