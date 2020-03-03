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
package com.google.idea.blaze.base.lang.buildfile.findusages;

import com.google.idea.blaze.base.lang.buildfile.language.BuildFileType;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageGroup;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.UsageView;
import com.intellij.usages.impl.rules.FileGroupingRule;
import com.intellij.usages.rules.UsageGroupingRule;
import com.intellij.usages.rules.UsageInFile;
import javax.annotation.Nullable;

/**
 * Allows us to customize the filename string in the 'find usages' dialog, rather than displaying
 * them all as 'BUILD'.
 */
public class BuildFileGroupingRule extends FileGroupingRule {

  public static UsageGroupingRule getGroupingRule(Project project, FileGroupingRule delegate) {
    return new BuildFileGroupingRule(project, delegate);
  }

  private final Project project;
  private final FileGroupingRule delegate;

  private BuildFileGroupingRule(Project project, FileGroupingRule delegate) {
    super(project);
    this.project = project;
    this.delegate = delegate;
  }

  @Override
  @Nullable
  public UsageGroup getParentGroupFor(Usage usage, UsageTarget[] targets) {
    // give the delegate a chance to refuse the usage
    UsageGroup base = delegate.getParentGroupFor(usage, targets);
    if (base == null || !(usage instanceof UsageInFile)) {
      return null;
    }
    VirtualFile vf = ((UsageInFile) usage).getFile();
    if (vf == null || vf.getFileType() != BuildFileType.INSTANCE) {
      return base;
    }
    return new FileUsageGroup(project, vf) {
      String name;

      @Override
      public void update() {
        super.update();
        if (isValid()) {
          name = BuildFile.getBuildFileString(project, vf.getPath());
        }
      }

      @Override
      public String getPresentableName() {
        return name;
      }

      @Override
      public String getText(UsageView view) {
        return name;
      }
    };
  }
}
