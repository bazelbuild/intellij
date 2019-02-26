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
package com.google.idea.blaze.base.filecache;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.idea.blaze.base.io.ModifiedTimeScanner;
import java.io.File;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;

/** Provides a diffing service for a collection of files. */
public final class FileDiffer {
  private FileDiffer() {}

  public static ImmutableMap<File, Long> updateFiles(
      @Nullable ImmutableMap<File, Long> oldState,
      Iterable<File> files,
      Collection<File> updatedFiles,
      Collection<File> removedFiles)
      throws InterruptedException, ExecutionException {
    ImmutableMap<File, Long> newState = readFileState(files);
    diffState(oldState, newState, updatedFiles, removedFiles);
    return newState;
  }

  public static ImmutableMap<File, Long> readFileState(Iterable<File> files)
      throws InterruptedException, ExecutionException {
    return ModifiedTimeScanner.readTimestamps(files);
  }

  public static <K, V> void diffState(
      @Nullable Map<K, V> oldState,
      Map<K, V> newState,
      Collection<K> updated,
      Collection<K> removed) {
    oldState = oldState != null ? oldState : ImmutableMap.of();

    // Find changed/new
    for (Map.Entry<K, V> entry : newState.entrySet()) {
      K key = entry.getKey();
      V value = entry.getValue();
      V oldValue = oldState.get(key);

      final boolean isUpdated = oldValue == null || !value.equals(oldValue);
      if (isUpdated) {
        updated.add(key);
      }
    }

    // Find removed
    Set<K> removedSet = Sets.newHashSet();
    removedSet.addAll(oldState.keySet());
    removedSet.removeAll(newState.keySet());
    removed.addAll(removedSet);
  }
}
