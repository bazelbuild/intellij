/*
 * Copyright 2022 The Bazel Authors. All rights reserved.
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

import static com.google.idea.blaze.common.Label.toLabelList;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.idea.blaze.common.Context;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.common.PrintOutput;
import com.google.idea.blaze.qsync.BuildGraphData.Location;
import com.google.idea.blaze.qsync.query.Query;
import com.google.idea.blaze.qsync.query.QuerySummary;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * A class that parses the proto output from a `blaze query --output=streamed_proto` invocation, and
 * yields a {@link BuildGraphData} instance derived from it.
 */
public class BlazeQueryParser {

  // Rules that will need to be built, whether or not the target is included in the
  // project.
  public static final ImmutableSet<String> ALWAYS_BUILD_RULE_TYPES =
      ImmutableSet.of(
          "java_proto_library", "java_lite_proto_library", "java_mutable_proto_library");
  private static final ImmutableSet<String> JAVA_RULE_TYPES =
      ImmutableSet.of("java_library", "java_binary", "kt_jvm_library_helper", "java_test");
  private static final ImmutableSet<String> ANDROID_RULE_TYPES =
      ImmutableSet.of(
          "android_library",
          "android_binary",
          "android_local_test",
          "android_instrumentation_test",
          "kt_android_library_helper");

  private final Context context;

  public BlazeQueryParser(Context context) {
    this.context = context;
  }

  private static boolean isJavaRule(String ruleClass) {
    return JAVA_RULE_TYPES.contains(ruleClass) || ANDROID_RULE_TYPES.contains(ruleClass);
  }

  public BuildGraphData parse(QuerySummary query) {
    context.output(PrintOutput.log("Analyzing project structure..."));

    Set<Path> packages = new HashSet<>();
    long now = System.nanoTime();

    BuildGraphData.Builder graphBuilder = BuildGraphData.builder();
    Map<Label, Label> sourceOwner = Maps.newHashMap();
    Map<Label, Set<Label>> ruleDeps = Maps.newHashMap();
    Set<Label> projectDeps = Sets.newHashSet();

    // A hacky collection the project state. This is the equivalent of the targetmap data:

    // An aggregation of all the dependencies of java rules
    Set<Label> deps = new HashSet<>();
    // All the project targets the aspect needs to build
    Set<Label> projectTargetsToBuild = new HashSet<>();
    // Counts of all kinds of rules
    Map<String, Integer> ruleCount = new HashMap<>();
    // All the direct dependencies from source files to things it needs outside the project
    Map<Label, Set<Label>> sourceDeps = new HashMap<>();
    for (Map.Entry<Label, Query.SourceFile> sourceFileEntry :
        query.getSourceFilesMap().entrySet()) {
      Location l = new Location(sourceFileEntry.getValue().getLocation());
      if (l.file.endsWith(Path.of("BUILD"))) {
        packages.add(l.file);
      }
      graphBuilder.locationsBuilder().put(sourceFileEntry.getKey(), l);
      graphBuilder.fileToTargetBuilder().put(l.file, sourceFileEntry.getKey());
    }
    for (Map.Entry<Label, Query.Rule> ruleEntry : query.getRulesMap().entrySet()) {
      String ruleClass = ruleEntry.getValue().getRuleClass();
      graphBuilder.targetToKindBuilder().put(ruleEntry.getKey(), ruleClass);
      ruleCount.compute(ruleClass, (k, v) -> (v == null ? 0 : v) + 1);
      if (isJavaRule(ruleClass)) {
        ImmutableSet<Label> thisSources =
            ImmutableSet.copyOf(toLabelList(ruleEntry.getValue().getSourcesList()));
        Set<Label> thisDeps = Sets.newHashSet(toLabelList(ruleEntry.getValue().getDepsList()));
        ruleDeps.computeIfAbsent(ruleEntry.getKey(), x -> Sets.newHashSet()).addAll(thisDeps);
        for (Label thisSource : thisSources) {
          // TODO Consider replace sourceDeps with a map of:
          //   (source target) -> (rules the include it)
          // This would involve modifying the "fewer dependencies" logic below, but may yield
          // a cleaner solution.
          Set<Label> currentDeps = sourceDeps.get(thisSource);
          if (currentDeps == null) {
            sourceDeps.put(thisSource, thisDeps);
            sourceOwner.put(thisSource, ruleEntry.getKey());
          } else {
            currentDeps.retainAll(thisDeps);
            if (ruleDeps.get(sourceOwner.get(thisSource)).size() > thisDeps.size()) {
              // Replace the owner with one with fewer dependencies
              sourceOwner.put(thisSource, ruleEntry.getKey());
            }
          }
        }
        graphBuilder.javaSourcesBuilder().addAll(thisSources);
        deps.addAll(thisDeps);

        if (ANDROID_RULE_TYPES.contains(ruleClass)) {
          graphBuilder.androidTargetsBuilder().add(ruleEntry.getKey());

          // Add android targets with aidl files as external deps so the aspect generates
          // the classes
          if (!ruleEntry.getValue().getIdlSourcesList().isEmpty()) {
            projectTargetsToBuild.add(ruleEntry.getKey());
          }
        }
      } else if (ALWAYS_BUILD_RULE_TYPES.contains(ruleClass)) {
        projectTargetsToBuild.add(ruleEntry.getKey());
      }
    }
    int nTargets = query.proto().getRulesCount();

    // Calculate all the dependencies outside the project.
    for (Label dep : deps) {
      if (!ruleDeps.containsKey(dep)) {
        projectDeps.add(dep);
      }
    }
    // Treat project targets the aspect needs to build as external deps
    projectDeps.addAll(projectTargetsToBuild);

    // Prune the source deps to only external ones.
    // This could be technically incorrect, because if there is a path from an internal rule
    // to an external rule we should consider it too. There is an implementation for it
    // in #transitiveDeps but that is done for one target, we have to do it for all before hand
    // here
    for (Entry<Label, Set<Label>> entry : sourceDeps.entrySet()) {
      entry.getValue().retainAll(projectDeps);
    }

    long elapsedMs = (System.nanoTime() - now) / 1000000L;
    context.output(PrintOutput.log("Processed %d targets, in %d ms", nTargets, elapsedMs));

    ArrayList<Entry<String, Integer>> entries = new ArrayList<>(ruleCount.entrySet());
    entries.sort(Entry.<String, Integer>comparingByValue().reversed());
    int shown = 0;
    int limit = 50;
    for (Entry<String, Integer> entry : entries) {
      if (entry.getValue() <= limit) {
        context.output(
            PrintOutput.log("[...] truncated %d rules with <= %d count", nTargets - shown, limit));
        break;
      }
      shown += entry.getValue();
      context.output(PrintOutput.log("%s: %d", entry.getKey(), entry.getValue()));
    }

    BuildGraphData graph =
        graphBuilder.sourceOwner(sourceOwner).ruleDeps(ruleDeps).projectDeps(projectDeps).build();

    context.output(PrintOutput.log("Found source files %d", graph.locations().size()));
    context.output(PrintOutput.log("Found %d targets as java sources", graph.javaSources().size()));
    context.output(PrintOutput.log("Found %d packages", packages.size()));
    context.output(PrintOutput.log("Found %d dependencies", deps.size()));
    context.output(
        PrintOutput.log(
            "Of which %d are to targets outside the project", graph.projectDeps().size()));

    int maxDeps = 0;
    Label worstSource = null;
    for (Entry<Label, Set<Label>> e : sourceDeps.entrySet()) {
      if (e.getValue().size() > maxDeps) {
        maxDeps = e.getValue().size();
        worstSource = e.getKey();
      }
    }
    context.output(PrintOutput.log("Source with most direct dependencies (%d) is:", maxDeps));
    context.output(PrintOutput.log("%s", worstSource));
    return graph;
  }
}
