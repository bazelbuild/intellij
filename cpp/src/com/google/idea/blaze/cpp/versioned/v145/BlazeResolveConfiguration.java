/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.cpp;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.base.ideinfo.RuleKey;
import com.google.idea.blaze.base.model.primitives.ExecutionRootPath;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.intellij.openapi.project.Project;
import com.jetbrains.cidr.execution.CidrBuildTarget;
import com.jetbrains.cidr.execution.CidrBuildTargetWithConfigurations;
import com.jetbrains.cidr.execution.CidrTargetHolder;
import java.io.File;
import java.util.List;
import javax.annotation.Nullable;
import javax.swing.Icon;

final class BlazeResolveConfiguration extends BlazeResolveConfigurationTemporaryBase
    implements CidrTargetHolder {

  public BlazeResolveConfiguration(
      Project project,
      WorkspacePathResolver workspacePathResolver,
      RuleKey ruleKey,
      ImmutableCollection<ExecutionRootPath> cSystemIncludeDirs,
      ImmutableCollection<ExecutionRootPath> cppSystemIncludeDirs,
      ImmutableCollection<ExecutionRootPath> quoteIncludeDirs,
      ImmutableCollection<ExecutionRootPath> cIncludeDirs,
      ImmutableCollection<ExecutionRootPath> cppIncludeDirs,
      ImmutableCollection<String> defines,
      ImmutableMap<String, String> features,
      File cCompilerExecutable,
      File cppCompilerExecutable,
      ImmutableList<String> cCompilerFlags,
      ImmutableList<String> cppCompilerFlags) {
    super(
        project,
        workspacePathResolver,
        ruleKey,
        cSystemIncludeDirs,
        cppSystemIncludeDirs,
        quoteIncludeDirs,
        cIncludeDirs,
        cppIncludeDirs,
        defines,
        features,
        cCompilerExecutable,
        cppCompilerExecutable,
        cCompilerFlags,
        cppCompilerFlags);
  }

  /** Workaround for b/30301958. TODO: Remove this once we move to CLion 162.1531.1 or later */
  @Override
  public CidrBuildTarget getTarget() {
    return new CidrBuildTargetWithConfigurations() {
      @Override
      public String getName() {
        return ruleKey.toString();
      }

      @Override
      public String getProjectName() {
        return project.getName();
      }

      @Nullable
      @Override
      public Icon getIcon() {
        return null;
      }

      @Override
      public boolean isExecutable() {
        return false;
      }

      @Override
      public List getBuildConfigurations() {
        return ImmutableList.of();
      }
    };
  }
}
