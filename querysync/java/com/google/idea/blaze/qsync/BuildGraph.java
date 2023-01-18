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
import java.io.IOException;
import java.util.List;

/** Keeps a reference to the most up-to date {@link BuildGraphData} instance. */
public class BuildGraph {

  private final Object lock = new Object();
  private BuildGraphData currentInstance = BuildGraphData.EMPTY;

  private final List<BuildGraphListener> listeners = Lists.newArrayList();

  public BuildGraph() {}

  public void addListener(BuildGraphListener listener) {
    synchronized (lock) {
      listeners.add(listener);
    }
  }

  public void setCurrent(Context context, BuildGraphData graphData) throws IOException {
    ImmutableList<BuildGraphListener> listeners;
    synchronized (lock) {
      currentInstance = graphData;
      listeners = ImmutableList.copyOf(this.listeners);
    }
    for (BuildGraphListener l : listeners) {
      l.graphCreated(context, graphData);
    }
  }

  public BuildGraphData getCurrent() {
    synchronized (lock) {
      return currentInstance;
    }
  }
}
