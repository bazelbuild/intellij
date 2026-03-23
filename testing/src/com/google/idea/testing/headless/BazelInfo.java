/*
 * Copyright 2026 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.idea.testing.headless;

import java.nio.file.Path;
import java.util.HashMap;

public record BazelInfo(Path executionRoot, Path outputBase, Path bazelBin) {

  public static BazelInfo parse(String output) {
    final var map = new HashMap<String, String>();
    for (final var line : output.split("\n")) {
      final var items = line.split(": ");
      if (items.length != 2) {
        continue;
      }

      map.put(items[0].trim(), items[1].trim());
    }

    return new BazelInfo(
        Path.of(map.get("execution_root")),
        Path.of(map.get("output_base")),
        Path.of(map.get("bazel-bin"))
    );
  }
}
