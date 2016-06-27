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

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.async.executor.BlazeExecutor;
import com.google.idea.blaze.base.io.FileAttributeProvider;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * Provides a diffing service for a collection of files.
 */
public class FileDiffService {
  private static Logger LOG = Logger.getInstance(FileDiffService.class);

  public static class State implements Serializable {
    private static final long serialVersionUID = 2L;
    Map<File, FileEntry> fileEntryMap;
  }

  static class FileEntry implements Serializable {
    private static final long serialVersionUID = 2L;

    public File file;
    public long timestamp;

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      FileEntry fileEntry = (FileEntry)o;
      return Objects.equal(timestamp, fileEntry.timestamp) &&
             Objects.equal(file, fileEntry.file);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(file, timestamp);
    }
  }

  @Nullable
  public State updateFiles(@Nullable State oldState,
                           @NotNull Iterable<File> files,
                           @NotNull List<File> updatedFiles,
                           @NotNull List<File> removedFiles) {
    Map<File, FileEntry> oldFiles = oldState != null
                                    ? oldState.fileEntryMap
                                    : ImmutableMap.of();

    List<FileEntry> fileEntryList = null;
    try {
      fileEntryList = updateTimeStamps(files);
    } catch (Exception e) {
      LOG.error(e);
      return null;
    }

    // Find changed/new
    for (FileEntry newFile : fileEntryList) {
      FileEntry oldFile = oldFiles.get(newFile.file);
      final boolean isNew = oldFile == null || newFile.timestamp != oldFile.timestamp;
      if (isNew) {
        updatedFiles.add(newFile.file);
      }
    }

    // Find removed
    Set<File> newFiles = Sets.newHashSet();
    for (File file : files) {
      newFiles.add(file);
    }
    for (File file : oldFiles.keySet()) {
      if (!newFiles.contains(file)) {
        removedFiles.add(file);
      }
    }
    ImmutableMap.Builder<File, FileEntry> fileMap = ImmutableMap.builder();
    for (FileEntry fileEntry : fileEntryList) {
      fileMap.put(fileEntry.file, fileEntry);
    }
    State newState = new State();
    newState.fileEntryMap = fileMap.build();
    return newState;
  }

  private static List<FileEntry> updateTimeStamps(@NotNull Iterable<File> fileList) throws Exception {
    final FileAttributeProvider fileAttributeProvider = FileAttributeProvider.getInstance();
    List<ListenableFuture<FileEntry>> futures = Lists.newArrayList();
    for (File file : fileList) {
      futures.add(submit(() -> {
                           FileEntry fileEntry = new FileEntry();
                           fileEntry.file = file;
                           fileEntry.timestamp = fileAttributeProvider.getFileModifiedTime(fileEntry.file);
                           return fileEntry;
                         }
      ));
    }
    return Futures.allAsList(futures).get();
  }

  private static <T> ListenableFuture<T> submit(Callable<T> callable) {
    return BlazeExecutor.getInstance().submit(callable);
  }
}
