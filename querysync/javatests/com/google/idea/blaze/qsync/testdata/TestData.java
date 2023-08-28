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
package com.google.idea.blaze.qsync.testdata;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.Arrays.stream;

import com.google.common.collect.ImmutableList;
import com.google.devtools.build.runfiles.Runfiles;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

public enum TestData {
  ANDROID_AIDL_SOURCE_QUERY("aidl"),
  ANDROID_LIB_QUERY("android"),
  JAVA_EXPORTED_DEP_QUERY("exports"),
  JAVA_LIBRARY_EXTERNAL_DEP_QUERY("externaldep"),
  JAVA_LIBRARY_INTERNAL_DEP_QUERY("internaldep", "nodeps"),
  JAVA_LIBRARY_MULTI_TARGETS("multitarget"),
  JAVA_LIBRARY_NESTED_PACKAGE("nested"),
  JAVA_LIBRARY_NO_DEPS_QUERY("nodeps"),
  JAVA_LIBRARY_PROTO_DEP_QUERY("protodep"),
  JAVA_LIBRARY_TRANSITIVE_DEP_QUERY("transitivedep", "externaldep"),
  BUILDINCLUDES_QUERY("buildincludes"),
  INSTRUMENTATIONTEST_QUERY("instrumentationtest");

  public final ImmutableList<Path> srcPaths;

  TestData(String... paths) {
    this.srcPaths = stream(paths).map(Path::of).collect(toImmutableList());
  }

  private static final String WORKSPACE_NAME = "intellij_with_bazel";

  public static final Path ROOT =
      Path.of(
          "querysync/javatests/com/google/idea/blaze/qsync/testdata");

  public static final String ROOT_PACKAGE = "//" + ROOT;

  public static Path getPathFor(TestData name) throws IOException {
    return Path.of(Runfiles.preload().unmapped().rlocation(WORKSPACE_NAME + "/" + ROOT))
        .resolve(name.toString().toLowerCase(Locale.ROOT));
  }

  public static ImmutableList<Path> getRelativeSourcePathsFor(TestData name) {
    return name.srcPaths.stream().map(ROOT::resolve).collect(toImmutableList());
  }
}
