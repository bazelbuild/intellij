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
import com.intellij.util.indexing.diagnostic.ProjectIndexingHistoryImpl;
import java.time.Duration;
import javax.annotation.Nullable;

/** #api213: inline into IndexingLoggerTest */
public class ProjectIndexingHistoryWrapper {
  private final ProjectIndexingHistoryImpl projectIndexingHistoryImpl;

  private ProjectIndexingHistoryWrapper(ProjectIndexingHistoryImpl projectIndexingHistoryImpl) {
    this.projectIndexingHistoryImpl = projectIndexingHistoryImpl;
  }

  public static ProjectIndexingHistoryWrapper create(Project project) {
    return new ProjectIndexingHistoryWrapper(
        // The value for 'wasFullIndexing' is chosen arbitrarily as it doesn't matter for our tests.
        new ProjectIndexingHistoryImpl(
            project, /* indexingReason= */ "", /* wasFullIndexing= */ true));
  }

  /** #api213: inline into IndexingLoggerTest */
  public void addProviderStatisticsWithMaybeIndexingVisibleTime(
      Project project, String fileSetName, @Nullable Duration expectedIndexingVisibleTime) {
    IndexingJobStatistics statistics = new IndexingJobStatistics(project, fileSetName);
    if (expectedIndexingVisibleTime != null) {
      statistics.setIndexingVisibleTime(expectedIndexingVisibleTime.toNanos());
    }
    projectIndexingHistoryImpl.addProviderStatistics(statistics);
  }

  public ProjectIndexingHistoryImpl getProjectIndexingHistory() {
    return projectIndexingHistoryImpl;
  }

  public void setIndexingTimes(
      Duration expectedIndexingDuration,
      Duration expectedUpdatingDuration,
      Duration expectedScanFilesDuration) {
    projectIndexingHistoryImpl.getTimes().setIndexingDuration(expectedIndexingDuration);
    projectIndexingHistoryImpl.getTimes().setTotalUpdatingTime(expectedUpdatingDuration.toNanos());
    projectIndexingHistoryImpl.getTimes().setScanFilesDuration(expectedScanFilesDuration);
  }
}
