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

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.BoundedTaskExecutor;

import java.io.File;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * Implementation for prefetcher.
 */
public class PrefetchServiceImpl implements PrefetchService {
  private static final int THREAD_COUNT = 32;
  private final ListeningExecutorService executor = MoreExecutors.listeningDecorator(
    new BoundedTaskExecutor(Executors.newFixedThreadPool(THREAD_COUNT), THREAD_COUNT));

  @Override
  public ListenableFuture<?> prefetchFiles(List<File> files, boolean synchronous) {
    List<ListenableFuture<?>> futures = Lists.newArrayList();
    for (Prefetcher prefetcher : Prefetcher.EP_NAME.getExtensions()) {
      futures.add(prefetcher.prefetchFiles(files, executor, synchronous));
    }
    return Futures.allAsList(futures);
  }

  @Override
  public void prefetchProjectFiles(Project project) {
    for (Prefetcher prefetcher : Prefetcher.EP_NAME.getExtensions()) {
      prefetcher.prefetchProjectFiles(project, executor);
    }
  }
}
