/*
 * Copyright 2021 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.model;

import com.google.idea.blaze.base.model.primitives.Label;
import com.intellij.openapi.project.Project;
import java.util.Optional;
import org.jetbrains.annotations.Nullable;

/**
 * Allows to pass a target name during instance creation and then will always resolve to that target
 * name.
 */
public final class FakeLibraryToTargetResolver implements LibraryToTargetResolver {

  @Nullable private final Label label;

  private FakeLibraryToTargetResolver(@Nullable Label label) {
    this.label = label;
  }

  public static FakeLibraryToTargetResolver create(@Nullable Label label) {
    return new FakeLibraryToTargetResolver(label);
  }

  @Override
  public Optional<Label> resolveLibraryToTarget(Project project, LibraryKey library) {
    return Optional.ofNullable(label);
  }
}
