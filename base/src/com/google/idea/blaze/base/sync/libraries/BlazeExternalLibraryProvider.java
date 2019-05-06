/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.sync.libraries;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider;
import java.io.File;

/**
 * {@link AdditionalLibraryRootsProvider} that needs to be handled by {@link
 * ExternalLibraryManager}.
 *
 * <p>Requires update to library files after sync and periodically on frame activation.
 */
public interface BlazeExternalLibraryProvider {
  String getLibraryName();

  ImmutableList<File> getLibraryFiles(Project project, BlazeProjectData projectData);
}
