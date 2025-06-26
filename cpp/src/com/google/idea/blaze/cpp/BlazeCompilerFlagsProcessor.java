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
package com.google.idea.blaze.cpp;

import com.google.common.collect.ImmutableList;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Processes compiler flags learned from the build system before passing them on to the IDE, in case
 * there is a mismatch between the build system's compiler and clangd bundled with the IDE.
 */
public interface BlazeCompilerFlagsProcessor {

  ExtensionPointName<BlazeCompilerFlagsProcessor.Provider> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.cpp.BlazeCompilerFlagsProcessorProvider");

  /** Returns a processor if it applies to the given project. */
  interface Provider {
    Optional<BlazeCompilerFlagsProcessor> getProcessor(Project project);
  }

  /** Convert a list of flags to a processed list of flags. */
  List<String> processFlags(List<String> flags);

  static ImmutableList<String> process(Project project, List<String> compilerFlags) {
    final var processors = Arrays.stream(EP_NAME.getExtensions())
        .map(provider -> provider.getProcessor(project))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .toList();

    for (final var processor : processors) {
      compilerFlags = processor.processFlags(compilerFlags);
    }

    return ImmutableList.copyOf(compilerFlags);
  }
}
