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
package com.google.idea.blaze.base.run.producers;

import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.lang.buildfile.search.BlazePackage;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.BlazeCommandRunConfigurationType;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import java.io.File;
import javax.annotation.Nullable;

/** Runs all tests in a single selected blaze package directory. */
public class AllInPackageBlazeConfigurationProducer
    extends BlazeRunConfigurationProducer<BlazeCommandRunConfiguration> {

  public AllInPackageBlazeConfigurationProducer() {
    super(BlazeCommandRunConfigurationType.getInstance());
  }

  @Override
  protected boolean doSetupConfigFromContext(
      BlazeCommandRunConfiguration configuration,
      ConfigurationContext context,
      Ref<PsiElement> sourceElement) {
    RunConfigurationContext testContext = getTestContext(context);
    if (testContext == null) {
      return false;
    }
    sourceElement.set(testContext.getSourceElement());
    return testContext.setupRunConfiguration(configuration);
  }

  @Override
  protected boolean doIsConfigFromContext(
      BlazeCommandRunConfiguration configuration, ConfigurationContext context) {
    RunConfigurationContext testContext = getTestContext(context);
    return testContext != null && testContext.matchesRunConfiguration(configuration);
  }

  @Nullable
  private static RunConfigurationContext getTestContext(ConfigurationContext context) {
    WorkspaceRoot root = WorkspaceRoot.fromProject(context.getProject());
    PsiElement location = context.getPsiLocation();
    if (!(location instanceof PsiDirectory)) {
      return null;
    }
    PsiDirectory dir = (PsiDirectory) location;
    if (!isInWorkspace(root, dir)) {
      return null;
    }
    // only check if the directory itself is a blaze package
    // TODO(brendandouglas): otherwise check off the EDT, and return PendingRunConfigurationContext?
    return BlazePackage.isBlazePackage(dir) ? fromDirectory(root, dir) : null;
  }

  @Nullable
  private static RunConfigurationContext fromDirectory(WorkspaceRoot root, PsiDirectory dir) {
    WorkspacePath packagePath = getWorkspaceRelativePath(root, dir.getVirtualFile());
    if (packagePath == null) {
      return null;
    }
    return RunConfigurationContext.fromKnownTarget(
        TargetExpression.allFromPackageRecursive(packagePath), BlazeCommandName.TEST, dir);
  }

  @Nullable
  private static WorkspacePath getWorkspaceRelativePath(WorkspaceRoot root, VirtualFile vf) {
    return root.workspacePathForSafe(new File(vf.getPath()));
  }

  private static boolean isInWorkspace(WorkspaceRoot root, PsiDirectory dir) {
    return root.isInWorkspace(dir.getVirtualFile());
  }
}
