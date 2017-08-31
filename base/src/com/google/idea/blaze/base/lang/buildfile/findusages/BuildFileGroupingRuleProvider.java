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
import com.intellij.usages.impl.FileStructureGroupRuleProvider;
import com.intellij.usages.impl.rules.FileGroupingRule;
import com.intellij.usages.rules.UsageGroupingRule;
import com.intellij.usages.rules.UsageInFile;
import javax.annotation.Nullable;
import javax.swing.Icon;

/**
 * Allows us to customize the filename string in the 'find usages' dialog, rather than displaying
 * them all as 'BUILD'.
 */
public class BuildFileGroupingRuleProvider implements FileStructureGroupRuleProvider {

  public static UsageGroupingRule getGroupingRule(Project project) {
    return new BuildFileGroupingRule(project);
  }

  @Override
  public UsageGroupingRule getUsageGroupingRule(Project project) {
    return getGroupingRule(project);
  }

  private static class BuildFileGroupingRule extends FileGroupingRule {

    private final Project project;

    BuildFileGroupingRule(Project project) {
      super(project);
      this.project = project;
    }

    @SuppressWarnings("MissingOverride") // #api171: added in 2017.2
    @Nullable
    public UsageGroup getParentGroupFor(Usage usage, UsageTarget[] targets) {
      if (!(usage instanceof UsageInFile)) {
        return null;
      }
      final VirtualFile virtualFile = ((UsageInFile) usage).getFile();
      if (virtualFile.getFileType() != BuildFileType.INSTANCE) {
        return null;
      }
      return new FileUsageGroup(project, virtualFile) {
        String name;

        @Override
        public void update() {
          if (isValid()) {
            super.update();
            name = BuildFile.getBuildFileString(project, virtualFile.getPath());
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

        @Override
        public Icon getIcon(boolean isOpen) {
          return null; // already shown by default usage group (which we can't remove...)
        }
      };
    }

    @Override
    public UsageGroup groupUsage(Usage usage) {
      return getParentGroupFor(usage, UsageTarget.EMPTY_ARRAY);
    }
  }
}
