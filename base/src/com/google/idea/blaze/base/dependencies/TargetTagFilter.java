/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.dependencies;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Implementations of this interface are able to filter targets for having
 * one of a set of tags.
 */

public interface TargetTagFilter {

  ExtensionPointName<TargetTagFilter> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.TargetTagFilter");

  static boolean hasProvider() {
    return EP_NAME.getExtensions().length != 0;
  }

  /**
   * This method will run a Bazel query to select for those targets having a tag
   * which matches one of the supplied {@code tags}.
   *
   * @param tags is a list of tags that targets are expected to have configured in
   *             order to be filtered in.
   * @param targets is a list of Bazel targets to filter.
   * @return a subset of the supplied targets that include one of the supplied {code tags}.
   */
  static List<TargetExpression> filterCodeGen(
      Project project,
      BlazeContext context,
      List<TargetExpression> targets,
      Set<String> tags) {
    return Arrays.stream(EP_NAME.getExtensions())
        .map(p -> p.doFilterCodeGen(project, context, targets, tags))
        .filter(Objects::nonNull)
        .findFirst()
        .orElse(ImmutableList.of());
  }

  /**
   * {@see #filterCodeGen}
   */
  @Nullable
  List<TargetExpression> doFilterCodeGen(
      Project project,
      BlazeContext context,
      List<TargetExpression> targets,
      Set<String> tags);

}
