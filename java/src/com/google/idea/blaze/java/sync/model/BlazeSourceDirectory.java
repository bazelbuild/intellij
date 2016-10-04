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
package com.google.idea.blaze.java.sync.model;

import com.google.common.base.Objects;
import java.io.File;
import java.io.Serializable;
import java.util.Comparator;
import javax.annotation.concurrent.Immutable;
import org.jetbrains.annotations.NotNull;

/** A source directory. */
@Immutable
public final class BlazeSourceDirectory implements Serializable {
  private static final long serialVersionUID = 2L;

  public static final Comparator<BlazeSourceDirectory> COMPARATOR =
      (o1, o2) ->
          String.CASE_INSENSITIVE_ORDER.compare(
              o1.getDirectory().getPath(), o2.getDirectory().getPath());

  @NotNull private final File directory;
  private final boolean isTest;
  private final boolean isGenerated;
  private final boolean isResource;
  @NotNull private final String packagePrefix;

  /** Bulider for source directory */
  public static class Builder {
    @NotNull private final File directory;
    @NotNull private String packagePrefix = "";
    private boolean isTest;
    private boolean isResource;
    private boolean isGenerated;

    private Builder(@NotNull File directory) {
      this.directory = directory;
    }

    public Builder setPackagePrefix(@NotNull String packagePrefix) {
      this.packagePrefix = packagePrefix;
      return this;
    }

    public Builder setTest(boolean isTest) {
      this.isTest = isTest;
      return this;
    }

    public Builder setResource(boolean isResource) {
      this.isResource = isResource;
      return this;
    }

    public Builder setGenerated(boolean isGenerated) {
      this.isGenerated = isGenerated;
      return this;
    }

    public BlazeSourceDirectory build() {
      return new BlazeSourceDirectory(directory, isTest, isResource, isGenerated, packagePrefix);
    }
  }

  @NotNull
  public static Builder builder(@NotNull String directory) {
    return new Builder(new File(directory));
  }

  @NotNull
  public static Builder builder(@NotNull File directory) {
    return new Builder(directory);
  }

  private BlazeSourceDirectory(
      @NotNull File directory,
      boolean isTest,
      boolean isResource,
      boolean isGenerated,
      @NotNull String packagePrefix) {
    this.directory = directory;
    this.isTest = isTest;
    this.isResource = isResource;
    this.isGenerated = isGenerated;
    this.packagePrefix = packagePrefix;
  }

  /** Returns the full path name of the root of a source directory. */
  @NotNull
  public File getDirectory() {
    return directory;
  }

  /** Returns {@code true} if the directory contains test sources. */
  public boolean getIsTest() {
    return isTest;
  }

  /** Returns {@code true} if the directory contains resources. */
  public boolean getIsResource() {
    return isResource;
  }

  /** Returns {@code true} if the directory contains generated files. */
  public boolean getIsGenerated() {
    return isGenerated;
  }

  /**
   * Returns the package prefix for the directory. If the directory is a source root, such as a
   * "src" directory, then this returns an empty string.
   */
  @NotNull
  public String getPackagePrefix() {
    return packagePrefix;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(directory, isTest, isResource, packagePrefix, isGenerated);
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof BlazeSourceDirectory)) {
      return false;
    }
    BlazeSourceDirectory that = (BlazeSourceDirectory) other;
    return directory.equals(that.directory)
        && packagePrefix.equals(that.packagePrefix)
        && isResource == that.isResource
        && isTest == that.isTest
        && isGenerated == that.isGenerated;
  }

  @Override
  public String toString() {
    return "BlazeSourceDirectory {\n"
        + "  directory: "
        + directory
        + "\n"
        + "  isTest: "
        + isTest
        + "\n"
        + "  isGenerated: "
        + isGenerated
        + "\n"
        + "  isResource: "
        + isResource
        + "\n"
        + "  packagePrefix: "
        + packagePrefix
        + "\n"
        + '}';
  }
}
