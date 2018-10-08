/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.prefetch;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.idea.common.concurrency.ConcurrencyUtil;
import com.intellij.util.concurrency.BoundedTaskExecutor;
import java.util.concurrent.Executors;

/** Shared executors for any prefetch/copy operations. */
public class FetchExecutor {
  private static final int THREAD_COUNT = 32;
  public static final ListeningExecutorService EXECUTOR =
      MoreExecutors.listeningDecorator(
          // #api181: use AppExecutorUtil.createBoundedApplicationPoolExecutor instead
          // AppExecutorUtil.createBoundedApplicationPoolExecutor(
          //     FetchExecutor.class.getSimpleName(),
          new BoundedTaskExecutor(
              Executors.newFixedThreadPool(
                  THREAD_COUNT, ConcurrencyUtil.namedDaemonThreadPoolFactory(FetchExecutor.class)),
              THREAD_COUNT));
}
