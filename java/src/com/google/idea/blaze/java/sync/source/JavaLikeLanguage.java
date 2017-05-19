/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.java.sync.source;

import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.intellij.openapi.extensions.ExtensionPointName;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * For languages similar to Java to reuse certain parts of the Java plugin. E.g., package prefix
 * calculation.
 */
public interface JavaLikeLanguage {
  ExtensionPointName<JavaLikeLanguage> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.JavaLikeLanguage");

  static Predicate<ArtifactLocation> getSourceFileMatcher() {
    final Set<String> fileExtensions =
        Arrays.stream(EP_NAME.getExtensions())
            .map(JavaLikeLanguage::getFileExtensions)
            .flatMap(Collection::stream)
            .collect(Collectors.toSet());
    return artifactLocation ->
        fileExtensions
            .stream()
            .anyMatch(extension -> artifactLocation.getRelativePath().endsWith(extension));
  }

  Set<String> getFileExtensions();

  /** Java is itself a Java-like language. */
  class Java implements JavaLikeLanguage {
    @Override
    public Set<String> getFileExtensions() {
      return ImmutableSet.of(".java");
    }
  }
}
