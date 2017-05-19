/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.python.run.filter;

import com.intellij.execution.filters.Filter;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;

/** Filters for python run configuration console output. */
public interface BlazePyFilterProvider {

  ExtensionPointName<BlazePyFilterProvider> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.BlazePyFilterProvider");

  static Collection<Filter> getPyFilters(Project project) {
    return Arrays.stream(EP_NAME.getExtensions())
        .map(provider -> provider.getFilter(project))
        .collect(Collectors.toList());
  }

  Filter getFilter(Project project);
}
