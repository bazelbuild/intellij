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

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.base.BlazeIntegrationTestCase;
import com.google.idea.blaze.base.EditorTestHelper;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.ExecutionRootPath;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoderImpl;
import com.google.idea.blaze.base.sync.workspace.BlazeRoots;
import com.google.idea.blaze.base.sync.workspace.WorkingSet;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolverImpl;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.junit.Before;

/** BUILD file specific integration test base */
public abstract class BuildFileIntegrationTestCase extends BlazeIntegrationTestCase {
  protected EditorTestHelper editorTest;

  @Before
  public final void doSetup() {
    mockBlazeProjectDataManager(getMockBlazeProjectData());
    editorTest = new EditorTestHelper(getProject(), testFixture);
  }

  /**
   * Creates a file with the specified contents and file path in the test project, and asserts that
   * it's parsed as a BuildFile
   */
  protected BuildFile createBuildFile(WorkspacePath workspacePath, String... contentLines) {
    PsiFile file = workspace.createPsiFile(workspacePath, contentLines);
    assertThat(file).isInstanceOf(BuildFile.class);
    return (BuildFile) file;
  }

  protected void assertFileContents(VirtualFile file, String... contentLines) {
    assertFileContents(fileSystem.getPsiFile(file), contentLines);
  }

  protected void assertFileContents(PsiFile file, String... contentLines) {
    String contents = Joiner.on("\n").join(contentLines);
    assertThat(file.getText()).isEqualTo(contents);
  }

  private BlazeProjectData getMockBlazeProjectData() {
    BlazeRoots fakeRoots =
        new BlazeRoots(
            null,
            ImmutableList.of(workspaceRoot.directory()),
            new ExecutionRootPath("out/crosstool/bin"),
            new ExecutionRootPath("out/crosstool/gen"),
            null);
    WorkspacePathResolver workspacePathResolver =
        new WorkspacePathResolverImpl(workspaceRoot, fakeRoots);
    ArtifactLocationDecoder artifactLocationDecoder =
        new ArtifactLocationDecoderImpl(fakeRoots, workspacePathResolver);
    return new BlazeProjectData(
        0,
        new TargetMap(ImmutableMap.of()),
        ImmutableMap.of(),
        fakeRoots,
        new WorkingSet(ImmutableList.of(), ImmutableList.of(), ImmutableList.of()),
        workspacePathResolver,
        artifactLocationDecoder,
        null,
        null,
        null,
        null);
  }
}
