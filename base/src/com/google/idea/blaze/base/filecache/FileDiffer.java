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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.idea.blaze.base.io.ModifiedTimeScanner;
import com.intellij.openapi.diagnostic.Logger;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

/** Provides a diffing service for a collection of files. */
public final class FileDiffer {
  private static Logger LOG = Logger.getInstance(FileDiffer.class);

  private FileDiffer() {}

  @Nullable
  public static ImmutableMap<File, Long> updateFiles(
      @Nullable ImmutableMap<File, Long> oldState,
      Iterable<File> files,
      List<File> updatedFiles,
      List<File> removedFiles) {
    ImmutableMap<File, Long> newState = readFileState(files);
    if (newState == null) {
      return null;
    }
    diffState(oldState, newState, updatedFiles, removedFiles);
    return newState;
  }

  @Nullable
  public static ImmutableMap<File, Long> readFileState(Iterable<File> files) {
    try {
      return ModifiedTimeScanner.readTimestamps(files);
    } catch (Exception e) {
      LOG.error(e);
      return null;
    }
  }

  public static <K, V> void diffState(
      @Nullable Map<K, V> oldState, Map<K, V> newState, List<K> updated, List<K> removed) {
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
