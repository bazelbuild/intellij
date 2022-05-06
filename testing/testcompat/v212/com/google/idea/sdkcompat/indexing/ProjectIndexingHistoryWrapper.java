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

import com.intellij.openapi.project.Project;
import com.intellij.util.indexing.diagnostic.IndexingJobStatistics;
import com.intellij.util.indexing.diagnostic.ProjectIndexingHistory;
import java.time.Duration;

/** #api212: inline into IndexingLoggerTest */
public class ProjectIndexingHistoryWrapper {
  private final ProjectIndexingHistory projectIndexingHistory;

  private ProjectIndexingHistoryWrapper(ProjectIndexingHistory projectIndexingHistory) {
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
    ProjectIndexingHistory projectIndexingHistory =
        new ProjectIndexingHistory(project, /* indexingReason= */ "");
    projectIndexingHistory.getTimes().setIndexingDuration(totalIndexingTime);
    projectIndexingHistory.getTimes().setTotalUpdatingTime(totalUpdatingTime.toNanos());
    projectIndexingHistory.getTimes().setScanFilesDuration(scanFilesDuration);

    if (!providerName.isEmpty()) {
      IndexingJobStatistics statistics = new IndexingJobStatistics(project, providerName);
      statistics.setIndexingVisibleTime(providerIndexingTime.toNanos());
      projectIndexingHistory.addProviderStatistics(statistics);
    }

    return new ProjectIndexingHistoryWrapper(projectIndexingHistory);
  }

  public ProjectIndexingHistory getProjectIndexingHistory() {
    return projectIndexingHistory;
  }
}
