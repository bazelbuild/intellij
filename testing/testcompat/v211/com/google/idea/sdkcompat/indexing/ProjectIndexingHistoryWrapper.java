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
    return new ProjectIndexingHistoryWrapper(new ProjectIndexingHistory(project));
  }

  /** #api203: inline into IndexingLoggerTest */
  @SuppressWarnings("UnstableApiUsage")
  public static void setIndexingVisibleTime(
      IndexingJobStatistics indexingStatistic, Duration expectedIndexingVisibleTime) {
    indexingStatistic.setIndexingVisibleTime(expectedIndexingVisibleTime.toNanos());
  }

  public ProjectIndexingHistory getProjectIndexingHistory() {
    return projectIndexingHistory;
  }

  public void addProviderStatistics(IndexingJobStatistics statistics) {
    projectIndexingHistory.addProviderStatistics(statistics);
  }

  public void setIndexingTimes(
      Duration expectedIndexingDuration,
      Duration expectedUpdatingDuration,
      Duration expectedScanFilesDuration) {
    projectIndexingHistory.getTimes().setIndexingDuration(expectedIndexingDuration);
    projectIndexingHistory.getTimes().setTotalUpdatingTime(expectedUpdatingDuration.toNanos());
    projectIndexingHistory.getTimes().setScanFilesDuration(expectedScanFilesDuration);
  }
}
