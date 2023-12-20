/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.qsync;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.google.idea.blaze.base.qsync.QuerySyncAsyncFileListener.SyncRequester;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase4;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class QuerySyncAsyncFileListenerTest extends LightJavaCodeInsightFixtureTestCase4 {

  // Analogous to a directory included in the projectview file
  private static final Path INCLUDED_DIRECTORY = Path.of("my/project");

  private SyncRequester mockSyncRequester;

  @Before
  public void setup() throws Exception {
    getFixture()
        .addFileToProject(
            INCLUDED_DIRECTORY.resolve("java/com/example/Class1.java").toString(),
            "package com.example;public class Class1 {}");
    getFixture()
        .addFileToProject(
            INCLUDED_DIRECTORY.resolve("BUILD").toString(), "java_library(name=\"java\",srcs=[])");

    getFixture()
        .getTempDirFixture()
        .findOrCreateDir(INCLUDED_DIRECTORY.resolve("submodule/java/com/example").toString());
    mockSyncRequester = mock(SyncRequester.class);
  }

  private void setupAutoSyncListener() {
    setupListener(true);
  }

  private void setupNoAutoSyncListener() {
    setupListener(false);
  }

  private void setupListener(boolean autoSync) {
    Path projectIncludePath =
        Path.of(LightPlatformTestCase.getSourceRoot().getPath()).resolve(INCLUDED_DIRECTORY);
    QuerySyncAsyncFileListener fileListener =
        new QuerySyncAsyncFileListener(
            mockSyncRequester, path -> path.startsWith(projectIncludePath), () -> autoSync);
    VirtualFileManager.getInstance()
        .addAsyncFileListener(fileListener, getFixture().getTestRootDisposable());
  }

  @Test
  public void projectFileAdded_requestsSync() {
    setupAutoSyncListener();

    getFixture()
        .addFileToProject(
            INCLUDED_DIRECTORY.resolve("java/com/example/Class2.java").toString(),
            "package com.example;public class Class2{}");
    verify(mockSyncRequester, atLeastOnce()).requestSync();
  }

  @Test
  public void projectFileAdded_autoSyncDisabled_neverRequestsSync() {
    setupNoAutoSyncListener();

    getFixture()
        .addFileToProject(
            INCLUDED_DIRECTORY.resolve("java/com/example/Class2.java").toString(),
            "package com.example;public class Class2{}");
    verify(mockSyncRequester, never()).requestSync();
  }

  @Test
  public void nonProjectFileAdded_neverRequestsSync() {
    setupAutoSyncListener();

    getFixture()
        .addFileToProject(
            "some/other/path/Class1.java", "package some.other.path;public class Class1{}");
    verify(mockSyncRequester, never()).requestSync();
  }

  @Test
  public void projectFileMoved_requestsSync() {
    setupAutoSyncListener();

    WriteAction.runAndWait(
        () ->
            getFixture()
                .moveFile(
                    INCLUDED_DIRECTORY.resolve("java/com/example/Class1.java").toString(),
                    INCLUDED_DIRECTORY.resolve("submodule/java/com/example").toString()));
    verify(mockSyncRequester, atLeastOnce()).requestSync();
  }

  @Test
  public void projectFileModified_nonBuildFile_doesNotRequestSync() throws Exception {
    setupAutoSyncListener();

    VirtualFile vf =
        getFixture()
            .findFileInTempDir(
                INCLUDED_DIRECTORY.resolve("java/com/example/Class1.java").toString());

    WriteAction.runAndWait(
        () ->
            vf.setBinaryContent(
                "/**LICENSE-TEXT*/package com.example;public class Class1{}".getBytes(UTF_8)));

    verify(mockSyncRequester, never()).requestSync();
  }

  @Test
  public void projectFileModified_buildFile_requestsSync() throws Exception {
    setupAutoSyncListener();

    VirtualFile vf = getFixture().findFileInTempDir(INCLUDED_DIRECTORY.resolve("BUILD").toString());

    WriteAction.runAndWait(
        () ->
            vf.setBinaryContent(
                "/**LICENSE-TEXT*/java_library(name=\"javalib\",srcs=[])".getBytes(UTF_8)));

    verify(mockSyncRequester, atLeastOnce()).requestSync();
  }
}
