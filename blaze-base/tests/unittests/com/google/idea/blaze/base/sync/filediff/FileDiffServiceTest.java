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
package com.google.idea.blaze.base.sync.filediff;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.async.executor.BlazeExecutor;
import com.google.idea.blaze.base.async.executor.MockBlazeExecutor;
import com.google.idea.blaze.base.experiments.ExperimentService;
import com.google.idea.blaze.base.experiments.MockExperimentService;
import com.google.idea.blaze.base.io.FileAttributeProvider;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.io.File;
import java.util.List;
import java.util.Map;

import static com.google.common.truth.Truth.assertThat;

/**
 * Tests for FileDiffService
 */
public class FileDiffServiceTest extends BlazeTestCase {
  private MockFileAttributeProvider fileModificationProvider;
  private FileDiffService fileDiffService;

  private static class MockFileAttributeProvider extends FileAttributeProvider {
    List<Long> times = Lists.newArrayList();
    int index;

    public MockFileAttributeProvider add(long time) {
      times.add(time);
      return this;
    }

    @Override
    public long getFileModifiedTime(@NotNull File file) {
      return times.get(index++);
    }
  }

  @Override
  protected void initTest(@NotNull Container applicationServices,
                          @NotNull Container projectServices) {
    super.initTest(applicationServices, projectServices);
    applicationServices.register(ExperimentService.class, new MockExperimentService());
    applicationServices.register(BlazeExecutor.class, new MockBlazeExecutor());

    this.fileModificationProvider = new MockFileAttributeProvider();
    applicationServices.register(FileAttributeProvider.class, fileModificationProvider);
    this.fileDiffService = new FileDiffService();
  }

  @Test
  public void testDiffWithDiffMethodTimestamp() throws Exception {
    Map<File, FileDiffService.FileEntry> oldFiles = fileMap(
      fileEntry("file1", 13),
      fileEntry("file2", 17),
      fileEntry("file3", 21)
    );
    FileDiffService.State oldState = new FileDiffService.State();
    oldState.fileEntryMap = oldFiles;
    List<File> fileList = ImmutableList.of(new File("file1"), new File("file2"));
    fileModificationProvider.add(13).add(122);

    List<File> newFiles = Lists.newArrayList();
    List<File> removedFiles = Lists.newArrayList();
    fileDiffService.updateFiles(
      oldState,
      fileList,
      newFiles,
      removedFiles
    );

    assertThat(newFiles).containsExactly(new File("file2"));
    assertThat(removedFiles).containsExactly(new File("file3"));
  }

  static Map<File, FileDiffService.FileEntry> fileMap(FileDiffService.FileEntry... fileEntries) {
    ImmutableMap.Builder<File, FileDiffService.FileEntry> builder = ImmutableMap.builder();
    for (FileDiffService.FileEntry fileEntry : fileEntries) {
      builder.put(fileEntry.file, fileEntry);
    }
    return builder.build();
  }

  static FileDiffService.FileEntry fileEntry(@NotNull String filePath, long timestamp) {
    FileDiffService.FileEntry fileEntry = new FileDiffService.FileEntry();
    fileEntry.file = new File(filePath);
    fileEntry.timestamp = timestamp;
    return fileEntry;
  }
}
