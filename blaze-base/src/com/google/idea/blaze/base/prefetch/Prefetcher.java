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

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;

import java.io.File;

/**
 * Prefetches files when a project is opened or roots change.
 */
public interface Prefetcher {
  ExtensionPointName<Prefetcher> EP_NAME = ExtensionPointName.create("com.google.idea.blaze.Prefetcher");

  /**
   * Prefetches the given list of files.
   *
   * It is the responsibility of the prefetcher to filter out any files it isn't interested in.
   *
   * @param synchronous A hint whether the prefetch should be complete when the returned future completes
   */
  ListenableFuture<?> prefetchFiles(Iterable<File> file,
                                    ListeningExecutorService executor,
                                    boolean synchrononous);

  /**
   * Prefetch any project files that this prefetcher is interested in.
   *
   * <p>The prefetch should be asynchronous.
   */
  void prefetchProjectFiles(Project project, ListeningExecutorService executor);
}
