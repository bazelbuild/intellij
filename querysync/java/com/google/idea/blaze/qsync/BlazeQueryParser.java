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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.devtools.build.lib.query2.proto.proto2api.Build.Attribute;
import com.google.devtools.build.lib.query2.proto.proto2api.Build.Target;
import com.google.devtools.build.lib.query2.proto.proto2api.Build.Target.Discriminator;
import com.google.idea.blaze.common.Context;
import com.google.idea.blaze.common.PrintOutput;
import com.google.idea.blaze.qsync.BuildGraphData.Location;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
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

  private final Path workspaceRoot;
  private final Context context;

  public BlazeQueryParser(Path workspaceRoot, Context context) {
    this.workspaceRoot = workspaceRoot;
    this.context = context;
  }


  private static boolean isJavaRule(String ruleClass) {
    return JAVA_RULE_TYPES.contains(ruleClass) || ANDROID_RULE_TYPES.contains(ruleClass);
  }

  public BuildGraphData parse(File protoFile) throws IOException {
    return parse(new BufferedInputStream(new FileInputStream(protoFile)));
  }

  public BuildGraphData parse(InputStream protoInputStream) throws IOException {
    context.output(PrintOutput.log("Analyzing project structure..."));

    Set<String> packages = new HashSet<>();
    long now = System.nanoTime();

    BuildGraphData.Builder graphBuilder = BuildGraphData.builder();
    Map<String, String> sourceOwner = Maps.newHashMap();
    Map<String, Set<String>> ruleDeps = Maps.newHashMap();
    Set<String> projectDeps = Sets.newHashSet();

    int nTargets = 0;

    // A hacky collection the project state. This is the equivalent of the targetmap data:

    // An aggregation of all the dependencies of java rules
    Set<String> deps = new HashSet<>();
    // All the project targets the aspect needs to build
    Set<String> projectTargetsToBuild = new HashSet<>();
    // Counts of all kinds of rules
    Map<String, Integer> ruleCount = new HashMap<>();
    // All the direct dependencies from source files to things it needs outside the project
    Map<String, Set<String>> sourceDeps = new HashMap<>();
    while (true) {
      Target target = Target.parseDelimitedFrom(protoInputStream);
      if (target == null) {
        break;
      }
      if (target.getType() == Discriminator.SOURCE_FILE) {
        Location l = new Location(target.getSourceFile().getLocation(), workspaceRoot);
        if (l.file.endsWith("/BUILD")) {
          packages.add(l.file);
        }
        graphBuilder.locationsBuilder().put(target.getSourceFile().getName(), l);
        String rel = workspaceRoot.relativize(Paths.get(l.file)).toString();
        graphBuilder.fileToTargetBuilder().put(rel, target.getSourceFile().getName());
      } else if (target.getType() == Discriminator.RULE) {
        ruleCount.compute(target.getRule().getRuleClass(), (k, v) -> (v == null ? 0 : v) + 1);
        if (isJavaRule(target.getRule().getRuleClass())) {
          Set<String> thisSources = new HashSet<>();
          Set<String> thisDeps = new HashSet<>();
          for (Attribute attribute : target.getRule().getAttributeList()) {
            if (attribute.getName().equals("srcs")) {
              thisSources.addAll(attribute.getStringListValueList());
            } else if (attribute.getName().equals("deps")) {
              thisDeps.addAll(attribute.getStringListValueList());
            }
          }
          ruleDeps
              .computeIfAbsent(target.getRule().getName(), x -> Sets.newHashSet())
              .addAll(thisDeps);
          for (String thisSource : thisSources) {
            // TODO Consider replace sourceDeps with a map of:
            //   (source target) -> (rules the include it)
            // This would involve modifying the "fewer dependencies" logic below, but may yield
            // a cleaner solution.
            Set<String> currentDeps = sourceDeps.get(thisSource);
            if (currentDeps == null) {
              sourceDeps.put(thisSource, thisDeps);
              sourceOwner.put(thisSource, target.getRule().getName());
            } else {
              currentDeps.retainAll(thisDeps);
              if (ruleDeps.get(sourceOwner.get(thisSource)).size() > thisDeps.size()) {
                // Replace the owner with one with fewer dependencies
                sourceOwner.put(thisSource, target.getRule().getName());
              }
            }
          }
          graphBuilder.javaSourcesBuilder().addAll(thisSources);
          deps.addAll(thisDeps);

          if (ANDROID_RULE_TYPES.contains(target.getRule().getRuleClass())) {
            graphBuilder.androidTargetsBuilder().add(target.getRule().getName());
          }
        } else if (ALWAYS_BUILD_RULE_TYPES.contains(target.getRule().getRuleClass())) {
          projectTargetsToBuild.add(target.getRule().getName());
        }
      }
      nTargets += 1;
    }
    // Calculate all the dependencies outside the project.
    for (String dep : deps) {
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
    for (Entry<String, Set<String>> entry : sourceDeps.entrySet()) {
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
    String worstSource = null;
    for (Entry<String, Set<String>> e : sourceDeps.entrySet()) {
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
