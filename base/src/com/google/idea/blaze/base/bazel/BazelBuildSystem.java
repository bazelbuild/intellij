/*
 * Copyright 2022 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.bazel;

import com.google.errorprone.annotations.MustBeClosed;
import com.google.idea.blaze.base.command.BlazeCommandRunner;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper;
import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.google.idea.blaze.base.settings.BuildBinaryType;
import com.google.idea.blaze.base.settings.BuildSystem;
import com.intellij.openapi.project.Project;

/**
 * Encapsulates information about a Bazel based build system.
 *
 * <p>The main purpose of this class is to provide instances of {@link BazelBinary} to encapsulate a
 * method of executing Bazel commands.
 */
public interface BazelBuildSystem {

  /** Strategy to use for builds that are part of a project sync. */
  enum SyncStrategy {
    /** Never parallelize sync builds. */
    SERIAL,
    /** Parallelize sync builds if it's deemed likely that doing so will be faster. */
    DECIDE_AUTOMATICALLY,
    /** Always parallelize sync builds. */
    PARALLEL,
  }

  /** Encapsulates a means of executing Bazel commands, often as a Bazel compatible binary. */
  interface BazelBinary {

    /**
     * @return The type of this Bazel interface. Used for logging purposes/
     */
    BuildBinaryType getType();

    /**
     * The path to the Bazel compatible binary on disk.
     *
     * <p>TODO(mathewi) This should really be fully encapsulated inside the runner returned from
     * {@link #getCommandRunner()} since it's not applicable to all implementations.
     */
    String getPath();

    /** Indicates if multiple invocations can be made at once. */
    boolean supportsParallelism();

    /**
     * Pass the result from `blaze info` to this {@code BazelBinary}. Some implementations require
     * this before a {@link BuildResultHelper} can be provided via {@link
     * #createBuildResultProvider()}.
     *
     * <p>TODO(mathewi) BlazeInfo should be fully encapsulated inside this interface so that callers
     * are not required to pass it in like this.
     *
     * @return this for convenience.
     */
    BazelBinary setBlazeInfo(BlazeInfo blazeInfo);

    /**
     * Create a {@link BuildResultHelper} instance. This instance must be closed when it is finished
     * with.
     */
    @MustBeClosed
    BuildResultHelper createBuildResultProvider();

    /**
     * @return a {@link BlazeCommandRunner} to be used to invoke this Blaze binary.
     *     <p>TODO(mathewi) Maybe better to roll BlazeCommandRunner into this interface?
     */
    BlazeCommandRunner getCommandRunner();
  }

  /** Returns the type of the build system. */
  BuildSystem type();

  /**
   * Get a Bazel binary.
   *
   * @param requestParallelismSupport Set to {@code true} to request a binary that supports
   *     simultaneous invocations. Setting this to {@code true} does not guarantee such a binary,
   *     and similarly a binary supporting parallelism may be returned regardless.
   */
  BazelBinary getBinary(Project project, boolean requestParallelismSupport);

  /** Return the strategy for remote syncs to be used with this build system. */
  SyncStrategy getSyncStrategy();
}
