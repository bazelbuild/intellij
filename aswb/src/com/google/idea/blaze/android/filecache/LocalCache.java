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

import java.nio.file.Path;
import javax.annotation.Nullable;

/**
 * Interface to be used by {@link com.google.idea.blaze.base.filecache.FileCache} which maintains
 * consistency between cache state and cache directory.
 */
public interface LocalCache {
  /**
   * Method to initialize the cache helper. This method should be called once before any other
   * public methods
   */
  void initialize();

  /** Removes all artifacts stored in the cache. */
  void clearCache();

  /** Update cache state and cache data to include what current cache directory have. */
  void refresh();

  /**
   * Returns the {@link Path} corresponding to the given key, or {@code null} if the file is not
   * tracked in cache.
   */
  @Nullable
  Path get(String cacheKey);
}
