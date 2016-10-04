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
package com.google.idea.blaze.base.filecache;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.async.executor.BlazeExecutor;
import com.google.idea.blaze.base.async.executor.MockBlazeExecutor;
import com.google.idea.blaze.base.io.FileAttributeProvider;
import com.google.idea.common.experiments.ExperimentService;
import com.google.idea.common.experiments.MockExperimentService;
import java.io.File;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link FileDiffer} */
@RunWith(JUnit4.class)
public class FileDifferTest extends BlazeTestCase {
  private MockFileAttributeProvider fileModificationProvider;

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
  protected void initTest(
      @NotNull Container applicationServices, @NotNull Container projectServices) {
    super.initTest(applicationServices, projectServices);
    applicationServices.register(ExperimentService.class, new MockExperimentService());
    applicationServices.register(BlazeExecutor.class, new MockBlazeExecutor());

    this.fileModificationProvider = new MockFileAttributeProvider();
    applicationServices.register(FileAttributeProvider.class, fileModificationProvider);
  }

  @Test
  public void testDiffWithDiffMethodTimestamp() throws Exception {
    ImmutableMap<File, Long> oldState =
        ImmutableMap.<File, Long>builder()
            .put(new File("file1"), 13L)
            .put(new File("file2"), 17L)
            .put(new File("file3"), 21L)
            .build();
    List<File> fileList = ImmutableList.of(new File("file1"), new File("file2"));
    fileModificationProvider.add(13).add(122);

    List<File> newFiles = Lists.newArrayList();
    List<File> removedFiles = Lists.newArrayList();
    FileDiffer.updateFiles(oldState, fileList, newFiles, removedFiles);

    assertThat(newFiles).containsExactly(new File("file2"));
    assertThat(removedFiles).containsExactly(new File("file3"));
  }
}
