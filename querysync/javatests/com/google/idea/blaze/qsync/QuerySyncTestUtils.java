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

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.common.Context;
import com.google.idea.blaze.common.vcs.VcsState;
import com.google.idea.blaze.qsync.query.ImplicitDepsProvider;
import com.google.idea.blaze.qsync.query.QuerySummary;
import com.google.idea.blaze.qsync.query.QuerySummaryProvider;
import com.google.idea.blaze.qsync.testdata.TestData;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

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
    return new QuerySummaryProvider(ImplicitDepsProvider.EMPTY)
        .getSummary(TestData.getPathFor(genQueryName).toFile());
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
}
