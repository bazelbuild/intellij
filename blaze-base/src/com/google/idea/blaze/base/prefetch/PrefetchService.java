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
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;

import java.io.File;
import java.util.List;

/**
 * Interface to request prefetching of files
 */
public interface PrefetchService {
  static PrefetchService getInstance() {
    return ServiceManager.getService(PrefetchService.class);
  }

  /**
   * Instructs all prefetchers to prefetch these files.
   *
   * @param files The files to prefetch
   * @param synchronous A hint whether the prefetch should be complete when the future completes.
   */
  ListenableFuture<?> prefetchFiles(List<File> files, boolean synchronous);

  /**
   * Instructs all prefetchers to prefetch any project files they're interested in.
   *
   * Should be asynchronous.
   */
  void prefetchProjectFiles(Project project);
}
