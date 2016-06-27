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
package com.google.idea.blaze.java.run.producers;

import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.java.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.java.run.BlazeCommandRunConfigurationType;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;

import javax.annotation.Nullable;

/**
 * Runs tests in all packages below selected directory
 */
public class AllInPackageBlazeConfigurationProducer extends BlazeTestRunConfigurationProducer<BlazeCommandRunConfiguration> {

  public AllInPackageBlazeConfigurationProducer() {
    super(BlazeCommandRunConfigurationType.getInstance());
  }

  @Override
  protected boolean doSetupConfigFromContext(
    BlazeCommandRunConfiguration configuration,
    ConfigurationContext context,
    Ref<PsiElement> sourceElement) {

    PsiDirectory dir = getTestDirectory(context);
    if (dir == null) {
      return false;
    }
    WorkspaceRoot root = WorkspaceRoot.fromProject(context.getModule().getProject());
    WorkspacePath packagePath = getWorkspaceRelativeDirectoryPath(root, dir);
    if (packagePath == null) {
      return false;
    }
    sourceElement.set(dir);

    configuration.setCommand(BlazeCommandName.TEST);
    configuration.setTarget(TargetExpression.allFromPackageRecursive(packagePath));
    configuration.setName(
      String.format("%s %s",
                    Blaze.buildSystemName(context.getProject()),
                    ExecutionBundle.message("test.in.scope.presentable.text", packagePath)));
    return true;
  }

  @Override
  protected boolean doIsConfigFromContext(
    BlazeCommandRunConfiguration configuration,
    ConfigurationContext context) {

    PsiDirectory dir = getTestDirectory(context);
    if (dir == null) {
      return false;
    }
    WorkspaceRoot root = WorkspaceRoot.fromProject(context.getModule().getProject());
    WorkspacePath packagePath = getWorkspaceRelativeDirectoryPath(root, dir);
    if (packagePath == null) {
      return false;
    }
    return configuration.getCommand() == BlazeCommandName.TEST &&
           configuration.getTarget() == TargetExpression.allFromPackageRecursive(packagePath);
  }

  @Nullable
  private static PsiDirectory getTestDirectory(ConfigurationContext context) {
    WorkspaceRoot root = WorkspaceRoot.fromProject(context.getModule().getProject());
    PsiElement location = context.getPsiLocation();
    if (location instanceof PsiDirectory) {
      PsiDirectory dir = (PsiDirectory) location;
      if (isInWorkspace(root, dir)) {
        return dir;
      }
    }
    return null;
  }

  @Nullable
  private static WorkspacePath getWorkspaceRelativeDirectoryPath(
    WorkspaceRoot root,
    PsiDirectory dir) {
    VirtualFile file = dir.getVirtualFile();
    if (isInWorkspace(root, dir)) {
      return root.workspacePathFor(file);
    }
    return null;
  }

  private static boolean isInWorkspace(WorkspaceRoot root,
                                       PsiDirectory dir) {
    return root.isInWorkspace(dir.getVirtualFile());
  }

}
