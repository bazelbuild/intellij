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
package com.google.idea.blaze.java.fastbuild;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import java.io.File;
import java.util.Collection;
import java.util.List;

interface FastBuildCompiler {

  void compile(BlazeContext context, CompileInstructions instructions) throws FastBuildException;

  @AutoValue
  abstract class CompileInstructions {
    abstract ImmutableSet<File> filesToCompile();

    abstract ImmutableList<File> classpath();

    abstract File outputDirectory();

    abstract ImmutableSet<String> annotationProcessorClassNames();

    abstract ImmutableList<File> annotationProcessorClasspath();

    static Builder builder() {
      return new AutoValue_FastBuildCompiler_CompileInstructions.Builder()
          .annotationProcessorClassNames(ImmutableList.of())
          .annotationProcessorClasspath(ImmutableList.of());
    }

    abstract Builder toBuilder();

    @AutoValue.Builder
    abstract static class Builder {
      abstract Builder filesToCompile(Collection<File> filesToCompile);

      abstract Builder classpath(List<File> classpath);

      abstract Builder outputDirectory(File outputDirectory);

      abstract Builder annotationProcessorClassNames(
          Collection<String> annotationProcessorClassNames);

      abstract Builder annotationProcessorClasspath(Collection<File> annotationProcessorClasspath);

      abstract CompileInstructions build();
    }
  }
}
