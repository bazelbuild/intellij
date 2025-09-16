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
package com.google.idea.blaze.base.model.primitives;

import com.google.auto.value.AutoValue;
import com.google.idea.blaze.base.ideinfo.ProjectDataInterner;
import com.google.idea.blaze.base.ideinfo.ProtoWrapper;
import com.intellij.openapi.util.io.FileUtil;
import java.io.File;
import java.nio.file.Path;
import javax.annotation.Nullable;

/**
 * An absolute or relative path returned from Blaze. If it is a relative path, it is relative to the execution root.
 */
@AutoValue
public abstract class ExecutionRootPath implements ProtoWrapper<String> {

  public abstract Path path();

  public static ExecutionRootPath create(Path path) {
    return new AutoValue_ExecutionRootPath(path);
  }

  public static ExecutionRootPath create(String path) {
    return create(Path.of(path));
  }

  public static ExecutionRootPath create(File file) {
    return create(file.toPath());
  }

  @Deprecated
  public File getAbsoluteOrRelativeFile() {
    return path().toFile();
  }

  public boolean isAbsolute() {
    return path().isAbsolute();
  }

  @Deprecated
  public File getFileRootedAt(File absoluteRoot) {
    final var path = path();
    if (path.isAbsolute()) {
      return path.toFile();
    }
    return new File(absoluteRoot, path.toString());
  }

  public Path getRootedAt(Path absoluteRoot) {
    final var path = path();
    if (path.isAbsolute()) {
      return path;
    }
    return absoluteRoot.resolve(path);
  }

  /**
   * Returns the relative {@link ExecutionRootPath} if {@code root} is an ancestor of {@code path} otherwise returns
   * null.
   */
  @Nullable
  public static ExecutionRootPath createAncestorRelativePath(File root, File path) {
    // We cannot find the relative path between an absolute and relative path.
    // The underlying code will make the relative path absolute
    // by rooting it at the current working directory which is almost never what you want.
    if (root.isAbsolute() != path.isAbsolute()) {
      return null;
    }
    if (!isAncestor(root.getPath(), path.getPath(), /* strict= */ false)) {
      return null;
    }
    String relativePath = FileUtil.getRelativePath(root.getAbsolutePath(), path.getAbsolutePath(), File.separatorChar);
    if (relativePath == null) {
      return null;
    }
    return ProjectDataInterner.intern(ExecutionRootPath.create(relativePath));
  }

  /**
   * If strict is false then this method returns true if the possibleParent is equal to possibleChild.
   */
  public static boolean isAncestor(ExecutionRootPath possibleParent, ExecutionRootPath possibleChild, boolean strict) {
    return isAncestor(
        possibleParent.getAbsoluteOrRelativeFile().getPath(),
        possibleChild.getAbsoluteOrRelativeFile().getPath(),
        strict
    );
  }

  /**
   * If strict is false then this method returns true if the possibleParent is equal to possibleChild.
   */
  public static boolean isAncestor(String possibleParentPath, ExecutionRootPath possibleChild, boolean strict) {
    return isAncestor(possibleParentPath, possibleChild.getAbsoluteOrRelativeFile().getPath(), strict);
  }

  /**
   * If strict is false then this method returns true if the possibleParent is equal to possibleChild.
   */
  public static boolean isAncestor(ExecutionRootPath possibleParent, String possibleChildPath, boolean strict) {
    return isAncestor(possibleParent.getAbsoluteOrRelativeFile().getPath(), possibleChildPath, strict);
  }

  /**
   * If strict is false then this method returns true if the possibleParent is equal to possibleChild.
   */
  public static boolean isAncestor(
      String possibleParentPath, String possibleChildPath, boolean strict) {
    return FileUtil.isAncestor(possibleParentPath, possibleChildPath, strict);
  }

  public static boolean pathsEqual(ExecutionRootPath a, ExecutionRootPath b) {
    return FileUtil.pathsEqual(a.getAbsoluteOrRelativeFile().getPath(), b.getAbsoluteOrRelativeFile().getPath());
  }

  public static ExecutionRootPath fromProto(String proto) {
    return ProjectDataInterner.intern(ExecutionRootPath.create(proto));
  }

  public static @Nullable ExecutionRootPath fromNullableProto(@Nullable String proto) {
    if (proto == null || proto.isBlank()) {
      return null;
    }

    return fromProto(proto);
  }

  @Override
  public String toProto() {
    return path().toString();
  }
}
