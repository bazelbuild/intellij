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
import java.time.Instant;

/** #api212: inline into IndexingLoggerTest */
public class ProjectIndexingHistoryWrapper {
  private final ProjectIndexingHistory projectIndexingHistory;

  private ProjectIndexingHistoryWrapper(ProjectIndexingHistory projectIndexingHistory) {
    this.projectIndexingHistory = projectIndexingHistory;
  }

  public static ProjectIndexingHistoryWrapper create(Project project) {
    return new ProjectIndexingHistoryWrapper(new ProjectIndexingHistory(project));
  }

  public ProjectIndexingHistory getProjectIndexingHistory() {
    return projectIndexingHistory;
  }

  /** #api203: inline into IndexingLoggerTest */
  @SuppressWarnings("UnstableApiUsage")
  public static void setIndexingVisibleTime(
      IndexingJobStatistics indexingStatistic, Duration expectedIndexingVisibleTime) {
    indexingStatistic.setTotalIndexingTime(expectedIndexingVisibleTime.toNanos());
  }

  public void addProviderStatistics(IndexingJobStatistics statistics) {
    projectIndexingHistory.addProviderStatistics(statistics);
  }

  public void setIndexingTimes(
      Duration expectedIndexingDuration,
      Duration expectedUpdatingDuration,
      Duration expectedScanFilesDuration) {
    Instant indexingStart = Instant.ofEpochMilli(1627913529);
    projectIndexingHistory.getTimes().setIndexingEnd(indexingStart.plus(expectedIndexingDuration));
    projectIndexingHistory.getTimes().setIndexingStart(indexingStart);

    Instant totalStart = Instant.ofEpochMilli(1627913533);
    projectIndexingHistory.getTimes().setTotalEnd(totalStart.plus(expectedUpdatingDuration));
    projectIndexingHistory.getTimes().setTotalStart(totalStart);

    Instant scanFilesEnd = Instant.ofEpochMilli(1327913533);
    projectIndexingHistory.getTimes().setScanFilesEnd(scanFilesEnd.plus(expectedScanFilesDuration));
    projectIndexingHistory.getTimes().setScanFilesStart(scanFilesEnd);
  }
}
