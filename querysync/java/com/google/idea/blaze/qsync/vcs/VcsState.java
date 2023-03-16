/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.qsync.vcs;

import com.google.common.collect.ImmutableSet;
import java.util.Objects;

/** State of the projects VCS at a point in time. */
public class VcsState {

  /**
   * Upstream/base revision or CL number. This usually represents the last checked-in change that
   * the users workspace contains.
   *
   * <p>This is treated as an opaque string for equality testing only.
   */
  public final String upstreamRevision;
  /** The set of files in the workspace that differ compared to {@link #upstreamRevision}. */
  public final ImmutableSet<WorkspaceFileChange> workingSet;

  public VcsState(String upstreamRevision, ImmutableSet<WorkspaceFileChange> workingSet) {
    this.upstreamRevision = upstreamRevision;
    this.workingSet = workingSet;
  }

  @Override
  public String toString() {
    return "VcsState{upstreamRevision='" + upstreamRevision + "', workingSet=" + workingSet + '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof VcsState)) {
      return false;
    }
    VcsState that = (VcsState) o;
    return upstreamRevision.equals(that.upstreamRevision) && workingSet.equals(that.workingSet);
  }

  @Override
  public int hashCode() {
    return Objects.hash(upstreamRevision, workingSet);
  }
}
