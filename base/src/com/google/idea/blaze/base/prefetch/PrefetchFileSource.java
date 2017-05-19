/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.prefetch;

import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.util.Collection;
import java.util.Set;

/** Provides a source of files to prefetch */
public interface PrefetchFileSource {
  ExtensionPointName<PrefetchFileSource> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.PrefetchFileSource");
  /** Adds any files or directories that we would be interested in prefetching. */
  void addFilesToPrefetch(
      Project project,
      ProjectViewSet projectViewSet,
      BlazeProjectData blazeProjectData,
      Collection<File> files);

  /** Returns any source file extensions that are a good candidate for the {@link Prefetcher}. */
  Set<String> prefetchSrcFileExtensions();
}
