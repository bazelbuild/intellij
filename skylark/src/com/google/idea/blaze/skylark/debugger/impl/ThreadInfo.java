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
package com.google.idea.blaze.skylark.debugger.impl;

import com.google.devtools.build.lib.skylarkdebugging.SkylarkDebuggingProtos.ThreadPausedState;
import javax.annotation.Nullable;

class ThreadInfo {
  final long id;
  final String name;
  @Nullable private volatile ThreadPausedState pausedState;

  ThreadInfo(long id, String name, @Nullable ThreadPausedState pausedState) {
    this.id = id;
    this.name = name;
    this.pausedState = pausedState;
  }

  void updatePausedState(@Nullable ThreadPausedState pausedState) {
    this.pausedState = pausedState;
  }

  @Nullable
  ThreadPausedState getPausedState() {
    return pausedState;
  }
}
