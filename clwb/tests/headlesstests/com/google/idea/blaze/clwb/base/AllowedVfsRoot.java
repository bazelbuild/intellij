package com.google.idea.blaze.clwb.base;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ObjectUtils;
import java.nio.file.Path;
import org.jetbrains.annotations.NotNull;

public class AllowedVfsRoot {

  private final Path root;
  private final boolean recursive;

  private AllowedVfsRoot(Path root, boolean recursive) {
    assert !root.isAbsolute() : "the path should be relative to the execution root";

    this.root = root;
    this.recursive = recursive;
  }

  public static AllowedVfsRoot flat(String path) {
    return new AllowedVfsRoot(Path.of(path).normalize(), false);
  }

  public static AllowedVfsRoot recursive(String path) {
    return new AllowedVfsRoot(Path.of(path).normalize(), true);
  }

  public boolean contains(Path path) {
    assert !path.isAbsolute() : "the path should be relative to the execution root";

    if (recursive) {
      return FileUtil.isAncestor(root.toFile(), path.toFile(), false);
    } else {
      return FileUtil.pathsEqual(root.toString(), ObjectUtils.coalesce(path.getParent(), Path.of("")).toString());
    }
  }

  @Override
  public @NotNull String toString() {
    if (recursive) {
      return root.toString();
    } else {
      return "|" + root.toString();
    }
  }
}

