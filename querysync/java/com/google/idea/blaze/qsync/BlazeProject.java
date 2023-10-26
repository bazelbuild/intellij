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
package com.google.idea.blaze.qsync;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.idea.blaze.common.Context;
import com.google.idea.blaze.qsync.project.BlazeProjectSnapshot;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;

/** Keeps a reference to the most up-to date {@link BlazeProjectSnapshot} instance. */
public class BlazeProject {

  private final Object lock = new Object();
  @Nullable private BlazeProjectSnapshot currentInstance = null;

  private final List<BlazeProjectListener> listeners = Lists.newArrayList();

  public BlazeProject() {}

  public void addListener(BlazeProjectListener listener) {
    synchronized (lock) {
      listeners.add(listener);
    }
  }

  public void setCurrent(Context context, BlazeProjectSnapshot newInstance) throws IOException {
    ImmutableList<BlazeProjectListener> listeners;
    synchronized (lock) {
      if (currentInstance == newInstance) {
        return;
      }
      currentInstance = newInstance;
      listeners = ImmutableList.copyOf(this.listeners);
    }
    for (BlazeProjectListener l : listeners) {
      l.onNewProjectSnapshot(context, newInstance);
    }
  }

  public Optional<BlazeProjectSnapshot> getCurrent() {
    synchronized (lock) {
      return Optional.ofNullable(currentInstance);
    }
  }
}
