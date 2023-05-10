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
package com.google.idea.blaze.qsync.query;

import static java.util.stream.Collectors.toCollection;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.qsync.query.Query.SourceFile;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class QuerySummaryTestUtil {

  private QuerySummaryTestUtil() {}

  public static Query.Summary createProtoForPackages(String... packages) {
    return createProtoForPackagesAndIncludes(ImmutableList.copyOf(packages), HashMultimap.create());
  }

  public static Query.Summary createProtoForPackagesAndIncludes(
      Collection<String> packages, Multimap<String, String> includes) {
    Set<Label> sourceFiles =
        packages.stream()
            .map(Label::of)
            .map(Label::getPackage)
            .map(p -> Label.fromPackageAndName(p, Path.of("BUILD")))
            .collect(toCollection(HashSet::new));
    includes.keySet().stream().map(Label::of).forEach(sourceFiles::add);

    Query.Summary.Builder builder = Query.Summary.newBuilder();
    for (String p : packages) {
      builder.putRules(p, Query.Rule.newBuilder().setRuleClass("java_library").build());
    }
    for (Label src : sourceFiles) {
      builder.putSourceFiles(
          src.toString(),
          SourceFile.newBuilder()
              .setLocation(src + ":1:1")
              .addAllSubinclude(includes.get(src.toString()))
              .build());
    }

    return builder.build();
  }

}
