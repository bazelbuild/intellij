package com.google.idea.sdkcompat.vcs;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.merge.MergeData;
import org.jetbrains.annotations.Nullable;

/** SDK adapter for creating {@link MergeData}. */
public final class MergeDataBuilder {
  private byte[] baseContent;
  private byte[] theirsContent;
  private byte[] yoursContent;

  @Nullable private VcsRevisionNumber theirsRevisionNumber;

  public void setBaseContent(byte[] baseContent) {
    this.baseContent = baseContent;
  }

  public void setTheirsContent(byte[] theirsContent) {
    this.theirsContent = theirsContent;
  }

  public void setYoursContent(byte[] yoursContent) {
    this.yoursContent = yoursContent;
  }

  public void setBaseRevisionNumber(@Nullable VcsRevisionNumber baseRevisionNumber) {}

  public void setTheirsRevisionNumber(@Nullable VcsRevisionNumber theirsRevisionNumber) {
    this.theirsRevisionNumber = theirsRevisionNumber;
  }

  public void setYoursRevisionNumber(@Nullable VcsRevisionNumber yoursRevisionNumber) {}

  public void setBaseFilePath(@Nullable FilePath baseFilePath) {}

  public void setTheirsFilePath(@Nullable FilePath theirsFilePath) {}

  public void setYoursFilePath(@Nullable FilePath yoursFilePath) {}

  public MergeData build() {
    MergeData mergeData = new MergeData();

    mergeData.ORIGINAL = baseContent;

    mergeData.LAST = theirsContent;
    mergeData.LAST_REVISION_NUMBER = theirsRevisionNumber;

    mergeData.CURRENT = yoursContent;

    return mergeData;
  }
}
