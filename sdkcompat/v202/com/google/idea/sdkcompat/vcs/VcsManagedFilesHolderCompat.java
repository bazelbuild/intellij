package com.google.idea.sdkcompat.vcs;

import com.intellij.openapi.vcs.changes.VcsIgnoredFilesHolder;

/**
 * Compat for {@link VcsManagedFilesHolder}. VcsIgnoredFilesHolder was renamed to
 * VcsManagedFilesHolder starting with 2021.2.
 *
 * <p>#api211
 */
public interface VcsManagedFilesHolderCompat extends VcsIgnoredFilesHolder {}
