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
package com.google.idea.blaze.base.lang.buildfile;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.base.BlazeIntegrationTestCase;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.lang.buildfile.psi.StringLiteral;
import com.google.idea.blaze.base.lang.buildfile.psi.util.PsiUtils;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.RuleMap;
import com.google.idea.blaze.base.model.primitives.ExecutionRootPath;
import com.google.idea.blaze.base.sync.workspace.BlazeRoots;
import com.google.idea.blaze.base.sync.workspace.WorkingSet;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolverImpl;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiFile;

/** BUILD file specific integration test base */
public abstract class BuildFileIntegrationTestCase extends BlazeIntegrationTestCase {

  @Override
  protected void doSetup() {
    mockBlazeProjectDataManager(getMockBlazeProjectData());
  }

  /**
   * Creates a file with the specified contents and file path in the test project, and asserts that
   * it's parsed as a BuildFile
   */
  protected BuildFile createBuildFile(String filePath, String... contentLines) {
    PsiFile file = createPsiFile(filePath, contentLines);
    assertThat(file).isInstanceOf(BuildFile.class);
    return (BuildFile) file;
  }

  protected void replaceStringContents(StringLiteral string, String newStringContents) {
    doRenameOperation(
        () -> {
          ASTNode node = string.getNode();
          node.replaceChild(
              node.getFirstChildNode(),
              PsiUtils.createNewLabel(string.getProject(), newStringContents));
        });
  }

  private BlazeProjectData getMockBlazeProjectData() {
    BlazeRoots fakeRoots =
        new BlazeRoots(
            null,
            ImmutableList.of(workspaceRoot.directory()),
            new ExecutionRootPath("out/crosstool/bin"),
            new ExecutionRootPath("out/crosstool/gen"));
    return new BlazeProjectData(
        0,
        new RuleMap(ImmutableMap.of()),
        fakeRoots,
        new WorkingSet(ImmutableList.of(), ImmutableList.of(), ImmutableList.of()),
        new WorkspacePathResolverImpl(workspaceRoot, fakeRoots),
        null,
        null,
        null,
        null);
  }
}
