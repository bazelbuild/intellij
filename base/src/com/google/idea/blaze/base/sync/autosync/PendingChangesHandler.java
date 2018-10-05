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
package com.google.idea.blaze.base.sync.autosync;

import com.google.common.collect.ImmutableSet;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tracks pending changes, and kicks off a task when no new changes have arrived in a given period
 * of time.
 *
 * <p>Use case: batching many changes arriving in a short period of time, then running a single task
 * over the full set of changes.
 */
abstract class PendingChangesHandler<V> {

  private static final int RETRY_DELAY_MILLIS = 10000;

  private final Set<V> pendingItems = Collections.synchronizedSet(new HashSet<>());

  private final Timer timer = new Timer("pendingChangesTimer", /* isDaemon */ true);
  private final int delayMillis;
  private final AtomicBoolean isTaskPending = new AtomicBoolean(false);

  private volatile long lastChangeTimeMillis;

  /**
   * @param delayMillis when no new changes have arrived for approximately this period of time the
   *     batched task is executed
   */
  PendingChangesHandler(int delayMillis) {
    this.delayMillis = delayMillis;
  }

  /**
   * Called when no new changes have arrived for a given period of time. Returns false if the task
   * cannot currently be run. In this case, the handler retries later.
   */
  abstract boolean runTask(ImmutableSet<V> changes);

  void queueChange(V item) {
    pendingItems.add(item);
    lastChangeTimeMillis = System.currentTimeMillis();
    // to minimize synchronization overhead, we don't explicitly cancel any existing task on each
    // change, but delay this until the pending task would otherwise run.
    if (isTaskPending.compareAndSet(false, true)) {
      queueTask(delayMillis);
    }
  }

  private void queueTask(long delayMillis) {
    timer.schedule(newTask(), delayMillis);
  }

  private TimerTask newTask() {
    return new TimerTask() {
      @Override
      public void run() {
        timerComplete();
      }
    };
  }

  /**
   * Run task if there have been no more changes since it was first requested, otherwise queue up
   * another task.
   */
  private void timerComplete() {
    long timeSinceLastEvent = System.currentTimeMillis() - lastChangeTimeMillis;
    if (timeSinceLastEvent < delayMillis) {
      // kick off another task and abort this one
      queueTask(delayMillis - timeSinceLastEvent);
      return;
    }
    ImmutableSet<V> items = retrieveAndClearPendingItems();
    if (runTask(items)) {
      isTaskPending.set(false);
    } else {
      pendingItems.addAll(items);
      queueTask(RETRY_DELAY_MILLIS);
    }
  }

  private ImmutableSet<V> retrieveAndClearPendingItems() {
    synchronized (pendingItems) {
      ImmutableSet<V> copy = ImmutableSet.copyOf(pendingItems);
      pendingItems.clear();
      return copy;
    }
  }
}
