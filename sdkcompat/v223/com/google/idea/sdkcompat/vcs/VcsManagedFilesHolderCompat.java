package com.google.idea.sdkcompat.vcs;

import com.intellij.openapi.vcs.changes.VcsManagedFilesHolder;

/**
 * Compat for {@link VcsManagedFilesHolder}. VcsIgnoredFilesHolder was renamed to
 * VcsManagedFilesHolder starting with 2021.2.
 *
 * <p>To cleanup delete interface and use VcsManagedFilesHolder directly.
 *
 * <p>#api211
 */
public interface VcsManagedFilesHolderCompat extends VcsManagedFilesHolder {}
