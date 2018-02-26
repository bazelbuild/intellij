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
package com.google.idea.blaze.kotlin.sync;

import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.model.primitives.WorkspaceType;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BuildSystem;
import com.google.idea.blaze.base.sync.workspace.WorkspaceHelper;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.openapi.project.Project;
import com.intellij.util.PlatformUtils;

final class KotlinUtils {

  // whether kotlin language support is enabled for internal users
  private static final BoolExperiment blazeKotlinSupport =
      new BoolExperiment("blaze.kotlin.support", false);

  static boolean isKotlinSupportEnabled(WorkspaceType workspaceType) {
    if (!workspaceType.getLanguages().contains(LanguageClass.JAVA)) {
      return false;
    }
    // enable kotlin support for internal AS users, and all external users
    return Blaze.defaultBuildSystem().equals(BuildSystem.Bazel)
        || (blazeKotlinSupport.getValue()
            && PlatformUtils.getPlatformPrefix().equals("AndroidStudio"));
  }

  private static final String
      RULES_REPO_PROJECT_PAGE = "https://github.com/bazelbuild/rules_kotlin",
      COMPILER_WORKSPACE_NAME = "com_github_jetbrains_kotlin";

  /**
   * The presence of the kotlin compiler repo is validated. See {@link
   * BlazeKotlinSyncPlugin#updateProjectStructure} for more details.
   */
  static boolean compilerRepoAbsentFromWorkspace(Project project) {
    WorkspaceRoot workspaceRoot =
        WorkspaceHelper.resolveExternalWorkspace(project, COMPILER_WORKSPACE_NAME);
    return workspaceRoot == null || !workspaceRoot.directory().exists();
  }

  static void issueUpdateRulesWarning(
      BlazeContext context, @SuppressWarnings("SameParameterValue") String because) {
    IssueOutput.issue(
            IssueOutput.Category.WARNING,
            because + ". Please update the rules by visiting " + RULES_REPO_PROJECT_PAGE + ".")
        .submit(context);
  }

  static void issueRulesMissingWarning(BlazeContext context) {
    IssueOutput.warn(
            "The Kotlin workspace has not been setup in this workspace. Visit "
                + RULES_REPO_PROJECT_PAGE
                + " , or remove the Kotlin language from the project view.")
        .submit(context);
  }
}
