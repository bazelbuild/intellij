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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import com.google.idea.blaze.common.BuildTarget;
import com.google.idea.blaze.common.Context;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.common.PrintOutput;
import com.google.idea.blaze.qsync.project.BuildGraphData;
import com.google.idea.blaze.qsync.project.BuildGraphData.Location;
import com.google.idea.blaze.qsync.query.PackageSet;
import com.google.idea.blaze.qsync.query.Query;
import com.google.idea.blaze.qsync.query.QuerySummary;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A class that parses the proto output from a `blaze query --output=streamed_proto` invocation, and
 * yields a {@link BuildGraphData} instance derived from it. Instances of this class are single use.
 */
public class BlazeQueryParser {

  // Rules that will need to be built, whether or not the target is included in the
  // project.
  public static final ImmutableSet<String> ALWAYS_BUILD_RULE_KINDS =
      ImmutableSet.of(
          "java_proto_library",
          "java_lite_proto_library",
          "java_mutable_proto_library",
          // Underlying rule for kt_jvm_lite_proto_library and kt_jvm_proto_library
          "kt_proto_library_helper",
          "_java_grpc_library",
          "_java_lite_grpc_library",
          "kt_grpc_library_helper",
          "java_stubby_library",
          "aar_import");

  private static final ImmutableSet<String> JAVA_RULE_TYPES =
      ImmutableSet.of(
          "java_library",
          "java_binary",
          "kt_jvm_library_helper",
          "java_test",
          "java_proto_library",
          "java_lite_proto_library",
          "java_mutable_proto_library",
          "_java_grpc_library",
          "_java_lite_grpc_library");
  private static final ImmutableSet<String> ANDROID_RULE_TYPES =
      ImmutableSet.of(
          "android_library",
          "android_binary",
          "android_local_test",
          "android_instrumentation_test",
          "kt_android_library_helper");

  private final Context<?> context;
  private final SetView<String> alwaysBuildRuleKinds;

  private final BuildGraphData.Builder graphBuilder = BuildGraphData.builder();
  private final PackageSet.Builder packages = new PackageSet.Builder();
  private final Map<Label, Set<Label>> ruleDeps = Maps.newHashMap();
  private final Set<Label> projectDeps = Sets.newHashSet();
  private final ImmutableMultimap.Builder<Label, Label> targetSources = ImmutableMultimap.builder();
  // All the project targets the aspect needs to build
  private final Set<Label> projectTargetsToBuild = new HashSet<>();
  // An aggregation of all the dependencies of java rules
  private final Set<Label> javaDeps = new HashSet<>();

  public BlazeQueryParser(Context<?> context, ImmutableSet<String> handledRuleKinds) {
    this.context = context;
    this.alwaysBuildRuleKinds = Sets.difference(ALWAYS_BUILD_RULE_KINDS, handledRuleKinds);
  }

  private static boolean isJavaRule(String ruleClass) {
    return JAVA_RULE_TYPES.contains(ruleClass) || ANDROID_RULE_TYPES.contains(ruleClass);
  }

  public BuildGraphData parse(QuerySummary query) {
    context.output(PrintOutput.log("Analyzing project structure..."));

    long now = System.nanoTime();

    // Counts of all kinds of rules
    Map<String, Integer> ruleCount = new HashMap<>();
    for (Map.Entry<Label, Query.SourceFile> sourceFileEntry :
        query.getSourceFilesMap().entrySet()) {
      Location l = new Location(sourceFileEntry.getValue().getLocation());
      if (l.file.endsWith(Path.of("BUILD"))) {
        packages.add(l.file.getParent());
      }
      graphBuilder.locationsBuilder().put(sourceFileEntry.getKey(), l);
      graphBuilder.fileToTargetBuilder().put(l.file, sourceFileEntry.getKey());
    }
    for (Map.Entry<Label, Query.Rule> ruleEntry : query.getRulesMap().entrySet()) {
      String ruleClass = ruleEntry.getValue().getRuleClass();
      ruleCount.compute(ruleClass, (k, v) -> (v == null ? 0 : v) + 1);

      BuildTarget.Builder buildTarget =
          BuildTarget.builder().setLabel(ruleEntry.getKey()).setKind(ruleClass);
      if (!ruleEntry.getValue().getTestApp().isEmpty()) {
        buildTarget.setTestApp(Label.of(ruleEntry.getValue().getTestApp()));
      }
      if (!ruleEntry.getValue().getInstruments().isEmpty()) {
        buildTarget.setInstruments(Label.of(ruleEntry.getValue().getInstruments()));
      }
      if (!ruleEntry.getValue().getCustomPackage().isEmpty()) {
        buildTarget.setCustomPackage(ruleEntry.getValue().getCustomPackage());
      }
      graphBuilder.targetMapBuilder().put(ruleEntry.getKey(), buildTarget.build());

      if (isJavaRule(ruleClass)) {
        visitJavaRule(query, ruleEntry.getKey(), ruleEntry.getValue());
      }
      if (alwaysBuildRuleKinds.contains(ruleClass)) {
        projectTargetsToBuild.add(ruleEntry.getKey());
      }
    }
    int nTargets = query.proto().getRulesCount();

    // Calculate all the dependencies outside the project.
    for (Label dep : javaDeps) {
      if (!query.getRulesMap().containsKey(dep)) {
        projectDeps.add(dep);
      }
    }
    // Treat project targets the aspect needs to build as external deps
    projectDeps.addAll(projectTargetsToBuild);

    long elapsedMs = (System.nanoTime() - now) / 1000000L;
    context.output(PrintOutput.log("%-10d Targets (%d ms):", nTargets, elapsedMs));

    BuildGraphData graph =
        graphBuilder
            .targetSources(targetSources.build())
            .ruleDeps(ruleDeps)
            .projectDeps(projectDeps)
            .packages(packages.build())
            .reverseDeps(calculateReverseDeps())
            .build();

    context.output(PrintOutput.log("%-10d Source files", graph.locations().size()));
    context.output(PrintOutput.log("%-10d Java sources", graph.javaSources().size()));
    context.output(PrintOutput.log("%-10d Packages", graph.packages().size()));
    context.output(PrintOutput.log("%-10d Dependencies", javaDeps.size()));
    context.output(PrintOutput.log("%-10d External dependencies", graph.projectDeps().size()));

    return graph;
  }

  private void visitJavaRule(QuerySummary query, Label label, Query.Rule rule) {
    graphBuilder.allTargetsBuilder().add(label);
    ImmutableSet<Label> thisSources =
        ImmutableSet.<Label>builder()
            .addAll(toLabelList(rule.getSourcesList()))
            .addAll(toLabelList(rule.getResourceFilesList()))
            .build();
    Set<Label> thisDeps = Sets.newHashSet(toLabelList(rule.getDepsList()));
    ruleDeps.computeIfAbsent(label, x -> Sets.newHashSet()).addAll(thisDeps);

    targetSources.putAll(label, thisSources);
    for (Label thisSource : thisSources) {
      // Require build step for targets with generated sources.
      if (!query.getSourceFilesMap().containsKey(thisSource)) {
        projectTargetsToBuild.add(label);
      }
    }
    graphBuilder.javaSourcesBuilder().addAll(thisSources);
    javaDeps.addAll(thisDeps);

    if (ANDROID_RULE_TYPES.contains(rule.getRuleClass())) {
      graphBuilder.androidTargetsBuilder().add(label);

      // Add android targets with aidl files as external deps so the aspect generates
      // the classes
      if (!rule.getIdlSourcesList().isEmpty()) {
        projectTargetsToBuild.add(label);
      }
      if (!rule.getManifest().isEmpty()) {
        targetSources.put(label, Label.of(rule.getManifest()));
      }
    }
  }

  private ImmutableMultimap<Label, Label> calculateReverseDeps() {
    ArrayListMultimap<Label, Label> map = ArrayListMultimap.create();
    ruleDeps
        .entrySet()
        .forEach(
            entry -> {
              for (Label dep : entry.getValue()) {
                map.put(dep, entry.getKey());
              }
            });
    return ImmutableMultimap.copyOf(map);
  }
}
