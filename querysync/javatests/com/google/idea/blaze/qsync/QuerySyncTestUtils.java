/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.qsync;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.auto.value.AutoValue;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.google.common.truth.Correspondence;
import com.google.idea.blaze.common.Context;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.common.vcs.VcsState;
import com.google.idea.blaze.qsync.query.QuerySummary;
import com.google.idea.blaze.qsync.testdata.TestData;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Helpers for unit tests. */
public class QuerySyncTestUtils {

  private QuerySyncTestUtils() {}

  public static final Context<?> NOOP_CONTEXT = new NoopContext();

  public static final Context<?> LOGGING_CONTEXT = new LoggingContext();

  public static final PackageReader EMPTY_PACKAGE_READER = p -> "";

  public static final VcsStateDiffer NO_CHANGES_DIFFER =
      (recent, earlier) -> Optional.of(ImmutableSet.of());

  public static final PackageReader PATH_INFERRING_PACKAGE_READER =
      QuerySyncTestUtils::inferJavaPackageFromPath;

  public static final Optional<VcsState> CLEAN_VCS_STATE =
      Optional.of(new VcsState("workspaceId", "1", ImmutableSet.of(), Optional.empty()));

  public static QuerySummary getQuerySummary(TestData genQueryName) throws IOException {
    return QuerySummary.create(genQueryName.getQueryOutputPath().toFile());
  }

  private static final ImmutableSet<String> JAVA_ROOT_DIRS = ImmutableSet.of("java", "javatests");

  public static String inferJavaPackageFromPath(Path p) {
    Path dir = p.getParent();
    for (int i = 0; i < dir.getNameCount(); ++i) {
      if (JAVA_ROOT_DIRS.contains(dir.getName(i).toString())) {
        Path packagePath = dir.subpath(i + 1, dir.getNameCount());
        return Joiner.on(".").join(packagePath);
      }
    }
    return "";
  }

  public static VcsStateDiffer differForFiles(Path... paths) {
    return (recent, earlier) -> Optional.of(ImmutableSet.copyOf(paths));
  }

  public static VcsStateDiffer noFilesChangedDiffer() {
    return differForFiles();
  }

  @AutoValue
  abstract static class PathPackage {
    abstract Path path();

    abstract String pkg();

    static PathPackage of(String path, String pkg) {
      return new AutoValue_QuerySyncTestUtils_PathPackage(Path.of(path), pkg);
    }
  }

  /**
   * Creates a dummy source jar at the specified destination with a package structure defined by
   * {@code pathPackages}
   */
  public static void createSrcJar(Path dest, PathPackage... pathPackages) throws IOException {
    Files.createDirectories(dest.getParent());
    try (ZipOutputStream srcJar =
        new ZipOutputStream(Files.newOutputStream(dest, StandardOpenOption.CREATE_NEW))) {
      for (PathPackage pathPackage : pathPackages) {
        ZipEntry src = new ZipEntry(pathPackage.path().toString());
        srcJar.putNextEntry(src);
        if (pathPackage.pkg().length() > 0) {
          srcJar.write(String.format("package %s;\n", pathPackage.pkg()).getBytes(UTF_8));
        } else {
          srcJar.write("// default package\n".getBytes(UTF_8));
        }
        srcJar.closeEntry();
      }
    }
  }

  /**
   * A correspondence that compares the repository-mapped form of labels.
   * For example, `@@rules_jvm_external~5.3~maven~com_google_guava_guava//jar:jar` should be
   * equal to `@@com_google_guava_guava//jar:jar`
   */
  public static final Correspondence<Label, Label> REPOSITORY_MAPPED_LABEL_CORRESPONDENCE =
          Correspondence.from(
                  (actual, expected) -> {
                    if (actual == null) {
                      return expected == null;
                    }
                    if (expected == null) {
                      return false;
                    }
                    String actualString = actual.toString();
                    String expectedString = expected.toString();
                    // Strip the @@
                    int actualDoubleAtIndex = actualString.indexOf("@@");
                    int expectedDoubleAtIndex = expectedString.indexOf("@@");
                    if (actualDoubleAtIndex != -1) {
                      actualString = actualString.substring(actualDoubleAtIndex + 2);
                    }
                    if (expectedDoubleAtIndex != -1) {
                      expectedString = expectedString.substring(expectedDoubleAtIndex + 2);
                    }
                    // Strip the "canonical prefix" (<some>~<path>~) from each label.
                    actualString = actualString.substring(actualString.lastIndexOf("~") + 1);
                    expectedString = expectedString.substring(expectedString.lastIndexOf("~") + 1);
                    return actualString.equals(expectedString);
                  },
                  "is repository-mapped equal to");
}
