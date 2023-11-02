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

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.idea.blaze.common.Label.toLabelList;
import static java.util.Arrays.stream;
import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import com.google.idea.blaze.common.Context;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.common.PrintOutput;
import com.google.idea.blaze.common.RuleKinds;
import com.google.idea.blaze.qsync.project.BuildGraphData;
import com.google.idea.blaze.qsync.project.BuildGraphData.Location;
import com.google.idea.blaze.qsync.project.LanguageClassProto.LanguageClass;
import com.google.idea.blaze.qsync.project.ProjectTarget;
import com.google.idea.blaze.qsync.query.PackageSet;
import com.google.idea.blaze.qsync.query.Query;
import com.google.idea.blaze.qsync.query.Query.Rule;
import com.google.idea.blaze.qsync.query.QuerySummary;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

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
          "aar_import",
          "java_import");

  private final Context<?> context;
  private final SetView<String> alwaysBuildRuleKinds;
  private final Supplier<Boolean> ccEnabledFlag;

  private final QuerySummary query;

  private final BuildGraphData.Builder graphBuilder = BuildGraphData.builder();
  private final PackageSet.Builder packages = new PackageSet.Builder();

  private final Set<Label> projectDeps = Sets.newHashSet();
  // All the project targets the aspect needs to build
  private final Set<Label> projectTargetsToBuild = new HashSet<>();
  // An aggregation of all the dependencies of java rules
  private final Set<Label> javaDeps = new HashSet<>();

  public BlazeQueryParser(
      QuerySummary query,
      Context<?> context,
      ImmutableSet<String> handledRuleKinds,
      Supplier<Boolean> ccEnabledFlag) {
    this.context = context;
    this.alwaysBuildRuleKinds = Sets.difference(ALWAYS_BUILD_RULE_KINDS, handledRuleKinds);
    this.query = query;
    this.ccEnabledFlag = ccEnabledFlag;
  }

  public BuildGraphData parse() {
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

      ProjectTarget.Builder targetBuilder = ProjectTarget.builder();

      targetBuilder.label(ruleEntry.getKey()).kind(ruleClass);
      if (!ruleEntry.getValue().getTestApp().isEmpty()) {
        targetBuilder.testApp(Label.of(ruleEntry.getValue().getTestApp()));
      }
      if (!ruleEntry.getValue().getInstruments().isEmpty()) {
        targetBuilder.instruments(Label.of(ruleEntry.getValue().getInstruments()));
      }
      if (!ruleEntry.getValue().getCustomPackage().isEmpty()) {
        targetBuilder.customPackage(ruleEntry.getValue().getCustomPackage());
      }

      if (RuleKinds.isJava(ruleClass)) {
        visitJavaRule(ruleEntry.getKey(), ruleEntry.getValue(), targetBuilder);
      }
      if (RuleKinds.isCc(ruleClass)) {
        visitCcRule(ruleEntry.getKey(), ruleEntry.getValue(), targetBuilder);
      }
      if (RuleKinds.isProtoSource(ruleClass)) {
        visitProtoRule(ruleEntry.getValue(), targetBuilder);
      }
      if (alwaysBuildRuleKinds.contains(ruleClass)) {
        projectTargetsToBuild.add(ruleEntry.getKey());
      }

      graphBuilder.targetMapBuilder().put(ruleEntry.getKey(), targetBuilder.build());
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

    BuildGraphData graph = graphBuilder.projectDeps(projectDeps).packages(packages.build()).build();

    context.output(PrintOutput.log("%-10d Source files", graph.locations().size()));
    context.output(PrintOutput.log("%-10d Java sources", graph.javaSources().size()));
    context.output(PrintOutput.log("%-10d Packages", graph.packages().size()));
    context.output(PrintOutput.log("%-10d Dependencies", javaDeps.size()));
    context.output(PrintOutput.log("%-10d External dependencies", graph.projectDeps().size()));

    return graph;
  }

  private void visitProtoRule(Query.Rule rule, ProjectTarget.Builder targetBuilder) {
    targetBuilder.sourceLabelsBuilder().addAll(expandFileGroupValues(rule.getSourcesList()));
  }

  private void visitJavaRule(Label label, Query.Rule rule, ProjectTarget.Builder targetBuilder) {
    graphBuilder.allTargetsBuilder().add(label);
    targetBuilder.languagesBuilder().add(LanguageClass.JAVA);
    ImmutableSet<Label> thisSources =
        expandFileGroupValues(rule.getSourcesList(), rule.getResourceFilesList());
    targetBuilder.sourceLabelsBuilder().addAll(thisSources);

    Set<Label> thisDeps = Sets.newHashSet(toLabelList(rule.getDepsList()));
    targetBuilder.depsBuilder().addAll(thisDeps);

    targetBuilder.runtimeDepsBuilder().addAll(toLabelList(rule.getRuntimeDepsList()));
    for (Label thisSource : thisSources) {
      addProjectTargetsToBuildIfGenerated(label, thisSource);
    }
    graphBuilder.javaSourcesBuilder().addAll(thisSources);
    javaDeps.addAll(thisDeps);

    if (RuleKinds.isAndroid(rule.getRuleClass())) {
      graphBuilder.androidTargetsBuilder().add(label);

      // Add android targets with aidl files as external deps so the aspect generates
      // the classes
      if (!rule.getIdlSourcesList().isEmpty()) {
        projectTargetsToBuild.add(label);
      }
      if (!rule.getManifest().isEmpty()) {
        targetBuilder.sourceLabelsBuilder().add(Label.of(rule.getManifest()));
      }
    }
  }

  private void visitCcRule(Label label, Query.Rule rule, ProjectTarget.Builder targetBuilder) {
    if (!ccEnabledFlag.get()) {
      return;
    }
    graphBuilder.allTargetsBuilder().add(label);
    targetBuilder.languagesBuilder().add(LanguageClass.CC);
    targetBuilder.coptsBuilder().addAll(rule.getCoptsList());
    ImmutableSet<Label> thisSources =
        expandFileGroupValues(rule.getSourcesList(), rule.getHdrsList());

    Set<Label> thisDeps = Sets.newHashSet(toLabelList(rule.getDepsList()));
    targetBuilder.depsBuilder().addAll(thisDeps);

    targetBuilder.sourceLabelsBuilder().addAll(thisSources);
    for (Label thisSource : thisSources) {
      addProjectTargetsToBuildIfGenerated(label, thisSource);
    }
  }

  /** Require build step for targets with generated sources. */
  private void addProjectTargetsToBuildIfGenerated(Label label, Label source) {
    if (!query.getSourceFilesMap().containsKey(source)) {
      projectTargetsToBuild.add(label);
    }
  }

  /** Returns a set of sources for a rule, expanding any in-project {@code filegroup} rules */
  private ImmutableSet<Label> expandFileGroupValues(List<String>... labelLists) {
    return stream(labelLists)
        .map(Label::toLabelList)
        .flatMap(List::stream)
        .map(this::expandFileGroups)
        .flatMap(Set::stream)
        .collect(toImmutableSet());
  }

  private ImmutableSet<Label> expandFileGroups(Label label) {
    if (!isFileGroup(label)) {
      return ImmutableSet.of(label);
    }
    Set<Label> visited = Sets.newHashSet();
    ImmutableSet.Builder<Label> result = ImmutableSet.builder();

    for (String source : requireNonNull(query.getRulesMap().get(label)).getSourcesList()) {
      Label asLabel = Label.of(source);
      if (visited.add(asLabel)) {
        result.addAll(expandFileGroups(asLabel));
      }
    }

    return result.build();
  }

  private boolean isFileGroup(Label label) {
    Rule rule = query.getRulesMap().get(label);
    if (rule == null) {
      return false;
    }
    return rule.getRuleClass().equals("filegroup");
  }
}
