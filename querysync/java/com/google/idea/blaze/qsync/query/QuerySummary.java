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

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.devtools.build.lib.query2.proto.proto2api.Build;
import com.google.devtools.build.lib.query2.proto.proto2api.Build.Target;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Summaries the output from a {@code query} invocation into just the data needed by the rest of
 * querysync.
 *
 * <p>The main purpose of the summarized output is to allow the outputs from multiple {@code query}
 * invocations to be combined. This enables delta updates to the project.
 *
 * <p>If extra data from the {@code query} invocation is needed by later stages of sync, that data
 * should be added to the {@link Query.Summary} proto and this code should be updated accordingly.
 * The proto should remain a simple mapping of data from the build proto, i.e. no complex
 * functionality should be added to this class. Non-trivial calculations based on the output of the
 * query belong in {@link com.google.idea.blaze.qsync.BlazeQueryParser} instead.
 *
 * <p>Instances of the the {@link Query.Summary} proto are maintained in memory so data should not
 * be added to it unnecessarily.
 */
public class QuerySummary {

  public static final QuerySummary EMPTY = new QuerySummary(Query.Summary.getDefaultInstance());

  private final Query.Summary proto;
  private final Supplier<ImmutableSet<Path>> packages = Suppliers.memoize(this::derivePackages);

  @VisibleForTesting
  QuerySummary(Query.Summary proto) {
    this.proto = proto;
  }

  public static QuerySummary create(InputStream protoInputStream) throws IOException {
    Map<String, Query.SourceFile> sourceFileMap = Maps.newHashMap();
    Map<String, Query.Rule> ruleMap = Maps.newHashMap();
    Build.Target target;
    while ((target = Target.parseDelimitedFrom(protoInputStream)) != null) {
      switch (target.getType()) {
        case SOURCE_FILE:
          Query.SourceFile sourceFile =
              Query.SourceFile.newBuilder()
                  .setLocation(target.getSourceFile().getLocation())
                  .build();
          sourceFileMap.put(target.getSourceFile().getName(), sourceFile);
          break;
        case RULE:
          // TODO We don't need all rules types in the proto since many are not user later on.
          //   We could filter the rules here, or even create rule-specific proto messages to
          //   reduce the size of the output proto.
          Query.Rule.Builder rule =
              Query.Rule.newBuilder().setRuleClass(target.getRule().getRuleClass());
          for (Build.Attribute a : target.getRule().getAttributeList()) {
            if (a.getName().equals("srcs")) {
              rule.addAllSources(a.getStringListValueList());
            } else if (a.getName().equals("deps")) {
              rule.addAllDeps(a.getStringListValueList());
            } else if (a.getName().equals("exports")) {
              // This is not strictly correct, as source files of rule with 'export' do not
              // depend on exported targets.
              rule.addAllDeps(a.getStringListValueList());
            } else if (a.getName().equals("idl_srcs")) {
              rule.addAllIdlSources(a.getStringListValueList());
            }
          }
          ruleMap.put(target.getRule().getName(), rule.build());
          break;
        default:
          break;
      }
    }
    return new QuerySummary(
        Query.Summary.newBuilder().putAllSourceFiles(sourceFileMap).putAllRules(ruleMap).build());
  }

  public static QuerySummary create(File protoFile) throws IOException {
    return create(new BufferedInputStream(new FileInputStream(protoFile)));
  }

  private static Path blazePackageFromTargetName(String target) {
    Preconditions.checkState(target.startsWith("//"), "Invalid target: %s", target);
    int colonPos = target.indexOf(':');
    Preconditions.checkState(colonPos > 1, "Invalid target: %s", target);
    return Path.of(target.substring(2, colonPos));
  }

  private ImmutableSet<Path> derivePackages() {
    return proto.getRulesMap().keySet().stream()
        .map(QuerySummary::blazePackageFromTargetName)
        .collect(toImmutableSet());
  }

  /**
   * Returns the set of build packages in the query output.
   *
   * <p>The packages are workspace relative paths that contain a BUILD file.
   */
  public ImmutableSet<Path> getPackages() {
    return packages.get();
  }

  /**
   * Returns the parent package of a given build package.
   *
   * <p>The parent package is not necessarily the same as the parent path: it may be an indirect
   * parent if there are paths that are not build packages (e.g. contain no BUILD file).
   */
  public Optional<Path> getParentPackage(Path buildPackage) {
    ImmutableSet<Path> packages = getPackages();
    Path packagePath = buildPackage.getParent();
    while (packagePath != null) {
      if (packages.contains(packagePath)) {
        return Optional.of(packagePath);
      }
      packagePath = packagePath.getParent();
    }
    return Optional.empty();
  }

  public Query.Summary getProto() {
    return proto;
  }

  /**
   * Applies a delta to this query summary.
   *
   * @param deltaQuery The output from a delta query. This should be a query summary created from
   *     the set packages affected by some workspace file changes, and encapsulated by {@link
   *     AffectedPackages}.
   * @param deletedPackages The set of deleted build packages from {@link AffectedPackages}.
   * @return A new query summary, the effects of the {@code deltaQuery} applied to {@code this}.
   */
  public QuerySummary applyDelta(QuerySummary deltaQuery, Set<Path> deletedPackages) {
    // copy all unaffected rules / source files to result:
    Map<String, Query.SourceFile> newSourceFiles = Maps.newHashMap();
    for (Map.Entry<String, Query.SourceFile> sfEntry : proto.getSourceFilesMap().entrySet()) {
      Path buildPackage = blazePackageFromTargetName(sfEntry.getKey());
      if (!(deletedPackages.contains(buildPackage)
          || deltaQuery.getPackages().contains(buildPackage))) {
        newSourceFiles.put(sfEntry.getKey(), sfEntry.getValue());
      }
    }
    Map<String, Query.Rule> newRules = Maps.newHashMap();
    for (Map.Entry<String, Query.Rule> ruleEntry : proto.getRulesMap().entrySet()) {
      Path buildPackage = blazePackageFromTargetName(ruleEntry.getKey());
      if (!(deletedPackages.contains(buildPackage)
          || deltaQuery.getPackages().contains(buildPackage))) {
        newRules.put(ruleEntry.getKey(), ruleEntry.getValue());
      }
    }

    // now add all rules / source files from the delta
    newSourceFiles.putAll(deltaQuery.getProto().getSourceFilesMap());
    newRules.putAll(deltaQuery.getProto().getRulesMap());
    return new QuerySummary(
        Query.Summary.newBuilder().putAllSourceFiles(newSourceFiles).putAllRules(newRules).build());
  }
}
