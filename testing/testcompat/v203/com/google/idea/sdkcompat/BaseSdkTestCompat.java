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
package com.google.idea.sdkcompat;

import com.intellij.openapi.project.Project;
import com.intellij.util.indexing.diagnostic.IndexingJobStatistics;
import com.intellij.util.indexing.diagnostic.ProjectIndexingHistory;
import java.time.Duration;
import java.time.Instant;

/**
 * Provides SDK compatibility shims for base plugin API classes, available to all IDEs during
 * test-time.
 */
public final class BaseSdkTestCompat {
  private BaseSdkTestCompat() {}

  /**
   * #api203: Doing duration calculations is not necessary anymore. Inline into IndexingLoggerTest.
   */
  @SuppressWarnings("UnstableApiUsage")
  public static void setIndexingTimes(
      ProjectIndexingHistory.IndexingTimes projectIndexingHistory,
      Duration expectedIndexingDuration,
      Duration expectedUpdatingDuration,
      Duration expectedScanFilesDuration) {

    Instant indexingStart = Instant.ofEpochMilli(1627913529);
    projectIndexingHistory.setIndexingEnd(indexingStart.plus(expectedIndexingDuration));
    projectIndexingHistory.setIndexingStart(indexingStart);

    Instant totalStart = Instant.ofEpochMilli(1627913533);
    projectIndexingHistory.setTotalEnd(totalStart.plus(expectedUpdatingDuration));
    projectIndexingHistory.setTotalStart(totalStart);

    Instant scanFilesEnd = Instant.ofEpochMilli(1327913533);
    projectIndexingHistory.setScanFilesEnd(scanFilesEnd.plus(expectedScanFilesDuration));
    projectIndexingHistory.setScanFilesStart(scanFilesEnd);
  }

  /** #api203: inline into IndexingLoggerTest */
  @SuppressWarnings("UnstableApiUsage")
  public static void setIndexingVisibleTime(
      IndexingJobStatistics indexingStatistic, Duration expectedIndexingVisibleTime) {
    indexingStatistic.setTotalIndexingTime(expectedIndexingVisibleTime.toNanos());
  }

  /** #api211 inline into IndexingLoggerTest */
  @SuppressWarnings("UnstableApiUsage")
  public static ProjectIndexingHistory initializeProjectIndexingHistory(Project project) {
    return new ProjectIndexingHistory(project);
  }
}
