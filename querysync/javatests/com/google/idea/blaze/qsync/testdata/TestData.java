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

import com.google.devtools.build.runtime.RunfilesPaths;
import java.nio.file.Path;
import java.util.Locale;

public enum TestData {
  ANDROID_AIDL_SOURCE_QUERY,
  ANDROID_LIB_QUERY,
  JAVA_EXPORTED_DEP_QUERY,
  JAVA_LIBRARY_EXTERNAL_DEP_QUERY,
  JAVA_LIBRARY_INTERNAL_DEP_QUERY,
  JAVA_LIBRARY_MULTI_TARGETS,
  JAVA_LIBRARY_NO_DEPS_QUERY,
  JAVA_LIBRARY_PROTO_DEP_QUERY,
  JAVA_LIBRARY_TRANSITIVE_DEP_QUERY,
  BUILDINCLUDES_QUERY;

  public static final Path ROOT =
      Path.of(
          "querysync/javatests/com/google/idea/blaze/qsync/testdata");

  public static final String ROOT_PACKAGE = "//" + ROOT;

  public static Path getPathFor(TestData name) {
    return RunfilesPaths.resolve(ROOT).resolve(name.toString().toLowerCase(Locale.ROOT));
  }
}
