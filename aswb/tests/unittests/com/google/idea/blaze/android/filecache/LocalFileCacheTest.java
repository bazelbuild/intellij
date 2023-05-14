/*
 * Copyright 2021 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.filecache;

import static com.google.common.truth.Truth.assertThat;
import static com.google.idea.blaze.android.filecache.LocalCacheUtils.CACHE_DATA_FILENAME;
import static com.google.idea.blaze.android.filecache.LocalCacheUtils.readJsonFromDisk;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.io.FileOperationProvider;
import com.google.idea.blaze.base.model.MockBlazeProjectDataBuilder;
import com.google.idea.blaze.base.model.MockBlazeProjectDataManager;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.prefetch.DefaultPrefetcher;
import com.google.idea.blaze.base.prefetch.RemoteArtifactPrefetcher;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.base.sync.workspace.MockArtifactLocationDecoder;
import com.google.idea.testing.IntellijRule;
import java.io.File;
import java.io.IOException;
import java.util.stream.Stream;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link LocalArtifactCache} */
@RunWith(JUnit4.class)
public class LocalFileCacheTest {
  @Rule public final IntellijRule intellijRule = new IntellijRule();

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();
  @Rule public TemporaryFolder cacheDirectory = new TemporaryFolder();

  private WorkspaceRoot workspaceRoot;

  private LocalFileCache localFileCache;

  @Before
  public void initTest() throws IOException {
    intellijRule.registerApplicationService(
        FileOperationProvider.class, new FileOperationProvider());
    intellijRule.registerApplicationService(
        RemoteArtifactPrefetcher.class, new DefaultPrefetcher());

    workspaceRoot = new WorkspaceRoot(temporaryFolder.getRoot());
    ArtifactLocationDecoder artifactLocationDecoder =
        new MockArtifactLocationDecoder() {
          @Override
          public File decode(ArtifactLocation artifactLocation) {
            return new File(workspaceRoot.directory(), artifactLocation.getRelativePath());
          }
        };

    intellijRule.registerProjectService(
        BlazeProjectDataManager.class,
        new MockBlazeProjectDataManager(
            MockBlazeProjectDataBuilder.builder()
                .setArtifactLocationDecoder(artifactLocationDecoder)
                .build()));

    localFileCache =
        new LocalFileCache(
            intellijRule.getProject(), "TestArtifactCache", cacheDirectory.getRoot().toPath());
  }

  @Test
  public void noStateFile_existingFiles_initializesWithAConsistentStateFile() throws IOException {
    cacheDirectory.newFile("untracked_file.2.jar");
    cacheDirectory.newFile("untracked_file.1.jar");

    localFileCache.initialize();

    File expectedCacheStateFile = new File(cacheDirectory.getRoot(), CACHE_DATA_FILENAME);
    assertThat(cacheDirectory.getRoot().listFiles()).asList().contains(expectedCacheStateFile);

    CacheData cacheData = readJsonFromDisk(expectedCacheStateFile);
    // check that all files referenced in the serialized cache data exists
    cacheData.getCacheEntries().stream()
        .map(CacheEntry::getFileName)
        .map(f -> new File(cacheDirectory.getRoot(), f))
        .forEach(f -> assertThat(f.exists()).isTrue());
  }

  @Test
  public void refresh_addsFileInDirectory() throws IOException {
    localFileCache.initialize();
    ImmutableList<File> files =
        ImmutableList.of(
            cacheDirectory.newFile("file_1.jar"),
            cacheDirectory.newFile("file_2.jar"),
            cacheDirectory.newFile("file_3.jar"));

    for (File file : files) {
      assertThat(file.exists()).isTrue();
    }

    localFileCache.refresh();

    // Check that the files are added to cache state
    ImmutableList<File> expectedFiles =
        Stream.concat(
                files.stream().map(f -> CacheEntry.forFile(f).getFileName()),
                Stream.of(CACHE_DATA_FILENAME))
            .map(f -> new File(cacheDirectory.getRoot(), f))
            .collect(ImmutableList.toImmutableList());
    assertThat(cacheDirectory.getRoot().listFiles())
        .asList()
        .containsExactlyElementsIn(expectedFiles);
  }
}
