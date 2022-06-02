package com.google.idea.sdkcompat.indexing;

import com.intellij.util.indexing.diagnostic.ProjectIndexingHistory.IndexingTimes;

/** #api212: inline into IndexingLogger */
public class IndexingTimesWrapper {

  private final IndexingTimes indexingTimes;

  public IndexingTimesWrapper(IndexingTimes indexingTimes) {
    this.indexingTimes = indexingTimes;
  }

  public IndexingTimes getTimes() {
    return indexingTimes;
  }
}
