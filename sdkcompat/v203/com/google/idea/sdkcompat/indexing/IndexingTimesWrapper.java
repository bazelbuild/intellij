package com.google.idea.sdkcompat.indexing;

import com.intellij.util.indexing.diagnostic.ProjectIndexingHistory.IndexingTimes;
import java.time.Duration;

/** #api212: inline into IndexingLogger */
public class IndexingTimesWrapper {

  private final IndexingTimes indexingTimes;

  public IndexingTimesWrapper(IndexingTimes indexingTimes) {
    this.indexingTimes = indexingTimes;
  }

  /** #api203: inline this method into IndexingLogger */
  public Duration getTotalUpdatingTime() {
    if (indexingTimes.getTotalEnd() == null || indexingTimes.getTotalStart() == null) {
      return Duration.ZERO;
    }
    return Duration.between(indexingTimes.getTotalStart(), indexingTimes.getTotalEnd());
  }

  /** #api203: inline this method into IndexingLogger */
  public Duration getScanFilesDuration() {
    if (indexingTimes.getScanFilesEnd() == null || indexingTimes.getScanFilesStart() == null) {
      return Duration.ZERO;
    }
    return Duration.between(indexingTimes.getScanFilesStart(), indexingTimes.getScanFilesEnd());
  }

  /** #api203: inline this method into IndexingLogger */
  public Duration getTotalIndexingTime() {
    if (indexingTimes.getIndexingDuration() == null) {
      return Duration.ZERO;
    }
    return indexingTimes.getIndexingDuration();
  }
}
