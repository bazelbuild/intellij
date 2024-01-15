package com.google.idea.blaze.common;

/** Type of a project based on the IDE configuration when it was created. */
public enum ProjectType {
  /** An aspect (legecy) sync project. */
  ASPECT_SYNC,
  /** A querysync project. */
  QUERY_SYNC,
  /**
   * UNKNOWN is used when BlazeImportSettings is not available for current project. In most of the
   * cases, it means the project is not a blaze project, and it does not have BlazeImportSettings.
   * But rarely, it could because that project is not available, BlazeImportSettings is not loaded/
   * fails to load.
   */
  UNKNOWN
}
