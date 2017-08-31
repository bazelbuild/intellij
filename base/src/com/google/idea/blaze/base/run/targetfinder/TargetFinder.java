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
package com.google.idea.blaze.base.run.targetfinder;

import com.google.common.collect.Iterables;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.model.primitives.Label;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import java.util.List;
import java.util.function.Predicate;
import javax.annotation.Nullable;

/** Searches BlazeProjectData for matching rules. */
public abstract class TargetFinder {
  public static TargetFinder getInstance() {
    return ServiceManager.getService(TargetFinder.class);
  }

  @Nullable
  public TargetIdeInfo targetForLabel(Project project, final Label label) {
    return findTarget(project, target -> target.key.label.equals(label) && target.isPlainTarget());
  }

  @Nullable
  public TargetIdeInfo findFirstTarget(Project project, Predicate<TargetIdeInfo> predicate) {
    return Iterables.getFirst(findTargets(project, predicate), null);
  }

  @Nullable
  private TargetIdeInfo findTarget(Project project, Predicate<TargetIdeInfo> predicate) {
    List<TargetIdeInfo> results = findTargets(project, predicate);
    assert results.size() <= 1;
    return Iterables.getFirst(results, null);
  }

  public abstract List<TargetIdeInfo> findTargets(
      Project project, Predicate<TargetIdeInfo> predicate);
}
