/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.sync;

/** The kind of sync. */
public enum SyncMode {
  /** Happens on startup, restores in-memory state */
  STARTUP,
  /** Partial / working set sync */
  PARTIAL,
  /** This is the standard incremental sync */
  INCREMENTAL,
  /** Full sync, can invalidate/redo work that an incremental sync does not */
  FULL,
  /** A partial sync, without any blaze build (i.e. updates directories / in-memory state only) */
  NO_BUILD;

  public static boolean involvesBlazeBuild(SyncMode mode) {
    switch (mode) {
      case STARTUP:
      case NO_BUILD:
        return false;
      case PARTIAL:
      case INCREMENTAL:
      case FULL:
        return true;
    }
    throw new AssertionError("SyncMode not handled: " + mode);
  }
}
