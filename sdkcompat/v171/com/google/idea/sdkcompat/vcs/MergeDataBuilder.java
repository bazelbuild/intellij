package com.google.idea.sdkcompat.vcs;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.merge.MergeData;
import org.jetbrains.annotations.Nullable;

/** SDK adapter for creating {@link MergeData}. */
// TODO(grl): Move to com.google.devtools.intellij.piper.resolve and make package-private
// once versions less than v171 have been deleted. We may as well keep the builder around,
// since it uses piper-relevant terminology and complies with Java style conventions.
public final class MergeDataBuilder {
  private byte[] baseContent;
  private byte[] theirsContent;
  private byte[] yoursContent;

  @Nullable private VcsRevisionNumber baseRevisionNumber;
  @Nullable private VcsRevisionNumber theirsRevisionNumber;
  @Nullable private VcsRevisionNumber yoursRevisionNumber;

  @Nullable private FilePath baseFilePath;
  @Nullable private FilePath theirsFilePath;
  @Nullable private FilePath yoursFilePath;

  public void setBaseContent(byte[] baseContent) {
    this.baseContent = baseContent;
  }

  public void setTheirsContent(byte[] theirsContent) {
    this.theirsContent = theirsContent;
  }

  public void setYoursContent(byte[] yoursContent) {
    this.yoursContent = yoursContent;
  }

  public void setBaseRevisionNumber(@Nullable VcsRevisionNumber baseRevisionNumber) {
    this.baseRevisionNumber = baseRevisionNumber;
  }

  public void setTheirsRevisionNumber(@Nullable VcsRevisionNumber theirsRevisionNumber) {
    this.theirsRevisionNumber = theirsRevisionNumber;
  }

  public void setYoursRevisionNumber(@Nullable VcsRevisionNumber yoursRevisionNumber) {
    this.yoursRevisionNumber = yoursRevisionNumber;
  }

  public void setBaseFilePath(@Nullable FilePath baseFilePath) {
    this.baseFilePath = baseFilePath;
  }

  public void setTheirsFilePath(@Nullable FilePath theirsFilePath) {
    this.theirsFilePath = theirsFilePath;
  }

  public void setYoursFilePath(@Nullable FilePath yoursFilePath) {
    this.yoursFilePath = yoursFilePath;
  }

  public MergeData build() {
    MergeData mergeData = new MergeData();

    mergeData.ORIGINAL = baseContent;
    mergeData.ORIGINAL_REVISION_NUMBER = baseRevisionNumber;
    mergeData.ORIGINAL_FILE_PATH = baseFilePath;

    mergeData.LAST = theirsContent;
    mergeData.LAST_REVISION_NUMBER = theirsRevisionNumber;
    mergeData.LAST_FILE_PATH = theirsFilePath;

    mergeData.CURRENT = yoursContent;
    mergeData.CURRENT_REVISION_NUMBER = yoursRevisionNumber;
    mergeData.CURRENT_FILE_PATH = yoursFilePath;

    return mergeData;
  }
}
