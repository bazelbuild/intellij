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
package com.google.idea.common.experiments;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class ExperimentsUtil {

  private static final HashFunction HASHER = Hashing.sha512();
  private static final Map<String, String> hashCache = new ConcurrentHashMap<>();

  private ExperimentsUtil() {}

  static String hashExperimentName(String name) {
    return hashCache.computeIfAbsent(
        name, s -> HASHER.hashString(s, StandardCharsets.UTF_8).toString());
  }
}
