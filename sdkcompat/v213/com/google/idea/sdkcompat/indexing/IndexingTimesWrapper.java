package com.google.idea.sdkcompat.indexing;

import com.intellij.util.indexing.diagnostic.IndexingTimes;
import java.time.Duration;

/** #api212: inline into IndexingLogger */
public class IndexingTimesWrapper {

  private final IndexingTimes indexingTimes;

  public IndexingTimesWrapper(IndexingTimes indexingTimes) {
    this.indexingTimes = indexingTimes;
  }

  public IndexingTimes getTimes() {
    return indexingTimes;
  }

  /** #api203: inline this method into IndexingLogger */
  public Duration getTotalUpdatingTime() {
    return Duration.ofNanos(getTimes().getTotalUpdatingTime());
  }

  /** #api203: inline this method into IndexingLogger */
  public Duration getScanFilesDuration() {
    return getTimes().getScanFilesDuration();
  }

  /** #api203: inline this method into IndexingLogger */
  public Duration getTotalIndexingTime() {
    return getTimes().getIndexingDuration();
  }
}
