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
