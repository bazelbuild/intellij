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
import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.project.Project;
import com.intellij.util.indexing.diagnostic.IndexingTimes;
import com.intellij.util.indexing.diagnostic.ProjectIndexingHistory;
import com.intellij.util.indexing.diagnostic.ScanningType;
import com.intellij.util.indexing.diagnostic.StatsPerFileType;
import com.intellij.util.indexing.diagnostic.StatsPerIndexer;
import com.intellij.util.indexing.diagnostic.dto.JsonDuration;
import com.intellij.util.indexing.diagnostic.dto.JsonFileProviderIndexStatistics;
import com.intellij.util.indexing.diagnostic.dto.JsonScanningStatistics;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/** #api212: inline into IndexingLoggerTest */
@SuppressWarnings("UnstableApiUsage")
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
                  /* totalIndexingVisibleTime= */ new JsonDuration(
                      /* nano= */ providerIndexingTime.toNanos()),
                  /* contentLoadingVisibleTime= */ new JsonDuration(/* nano= */ 0),
		  /* readActionWaitingVisibleTime= */ new JsonDuration(/* nano= */0),
                  /* numberOfTooLargeForIndexingFiles= */ 0,
                  /* slowIndexedFiles= */ ImmutableList.of(),
                  /* filesFullyIndexedByExtensions= */ ImmutableList.of(),
                  /* isAppliedAllValuesSeparately= */ false,
                  /* separateApplyingIndexesVisibleTime= */ new JsonDuration(/* nano= */ 0),
                  /* indexedFiles= */ ImmutableList.of()));
    }

    return new ProjectIndexingHistoryWrapper(
        new FakeProjectIndexingHistory(
            project,
            provider,
            new FakeIndexingTimes(totalIndexingTime, totalUpdatingTime, scanFilesDuration)));
  }

  public ProjectIndexingHistory getProjectIndexingHistory() {
    return projectIndexingHistory;
  }

  /**
   * #api212: review fields and consider inlining this class into IndexingLoggerTest since new
   * versions only add/remove fields, and we can just omit @Override (with an @SuppressWarnings and
   * #api annotation
   */
  private static final class FakeProjectIndexingHistory implements ProjectIndexingHistory {

    private final Project project;
    private final ImmutableList<JsonFileProviderIndexStatistics> statistics;
    private final FakeIndexingTimes fakeIndexingTimes;

    public FakeProjectIndexingHistory(
        Project project,
        ImmutableList<JsonFileProviderIndexStatistics> statistics,
        FakeIndexingTimes fakeIndexingTimes) {
      this.project = project;
      this.statistics = statistics;
      this.fakeIndexingTimes = fakeIndexingTimes;
    }

    @Override
    public String getIndexingReason() {
      return "";
    }

    @Override
    public long getIndexingSessionId() {
      return 0;
    }

    @Override
    public Project getProject() {
      return project;
    }

    @Override
    public ImmutableList<JsonFileProviderIndexStatistics> getProviderStatistics() {
      return statistics;
    }

    @Override
    public ImmutableList<JsonScanningStatistics> getScanningStatistics() {
      return ImmutableList.of();
    }

    @Override
    public IndexingTimes getTimes() {
      return fakeIndexingTimes;
    }

    @Override
    public ImmutableMap<String, StatsPerFileType> getTotalStatsPerFileType() {
      return ImmutableMap.of();
    }

    @Override
    public ImmutableMap<String, StatsPerIndexer> getTotalStatsPerIndexer() {
      return ImmutableMap.of();
    }

    @Override
    public double getVisibleTimeToAllThreadsTimeRatio() {
      return 0;
    }
  }

  /**
   * #api212: review fields and consider inlining this class into IndexingLoggerTest since new
   * versions only add/remove fields, and we can just omit @Override (with an @SuppressWarnings and
   * #api annotation
   */
  private static final class FakeIndexingTimes implements IndexingTimes {

    private final Duration indexingDuration;
    private final Duration totalUpdatingTime;
    private final Duration scanFilesDuration;

    public FakeIndexingTimes(
        Duration indexingDuration, Duration updatingDuration, Duration scanFilesDuration) {
      this.indexingDuration = indexingDuration;
      this.totalUpdatingTime = updatingDuration;
      this.scanFilesDuration = scanFilesDuration;
    }

    @Override
    public boolean getAppliedAllValuesSeparately() {
      return false;
    }

    @Override
    public Duration getContentLoadingVisibleDuration() {
      return Duration.ZERO;
    }

    @Override
    public Duration getCreatingIteratorsDuration() {
      return Duration.ZERO;
    }

    @Override
    public void setCreatingIteratorsDuration(Duration duration) {}

    @Override
    public Duration getIndexExtensionsDuration() {
      return Duration.ZERO;
    }

    @Override
    public Duration getIndexingDuration() {
      return indexingDuration;
    }

    @Override
    public String getIndexingReason() {
      return "";
    }

    @Override
    public Duration getPushPropertiesDuration() {
      return Duration.ZERO;
    }

    @Override
    public Duration getScanFilesDuration() {
      return scanFilesDuration;
    }

    @Override
    public long getSeparateValueApplicationVisibleTime() {
      return 0;
    }

    @Override
    public Duration getSuspendedDuration() {
      return Duration.ZERO;
    }

    @Override
    public long getTotalUpdatingTime() {
      return totalUpdatingTime.toNanos();
    }

    @Override
    public ZonedDateTime getUpdatingEnd() {
      return LocalDateTime.MIN.atZone(ZoneOffset.UTC);
    }

    @Override
    public ZonedDateTime getUpdatingStart() {
      return LocalDateTime.MIN.atZone(ZoneOffset.UTC);
    }

    @Override
    public ScanningType getScanningType() {
      return ScanningType.PARTIAL;
    }

    @Override
    public boolean getWasInterrupted() {
      return false;
    }
  }
}
