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
package com.google.idea.blaze.base.qsync;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.devtools.build.lib.query2.proto.proto2api.Build.Attribute;
import com.google.devtools.build.lib.query2.proto.proto2api.Build.Target;
import com.google.devtools.build.lib.query2.proto.proto2api.Build.Target.Discriminator;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.PrintOutput;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/** The build graph of all the rules that make up the project. */
public class BuildGraph {
  private static final ImmutableSet<String> JAVA_RULE_TYPES =
      ImmutableSet.of("java_library", "java_binary", "kt_jvm_library_helper", "java_test");
  private static final ImmutableSet<String> ANDROID_RULE_TYPES =
      ImmutableSet.of(
          "android_library",
          "android_binary",
          "android_local_test",
          "android_instrumentation_test",
          "kt_android_library_helper");

  // A map from target to file on disk for all source files
  private final Map<String, Location> locations;
  // A set of all the targets that show up in java rules 'src' attributes
  private final Set<String> javaSources;
  // A map from a file path to its target
  private final Map<String, String> fileToTarget;
  // From source target to the rule that builds it. If multiple one is picked.
  private final Map<String, String> sourceOwner;
  // All the dependencies from source files to things it needs outside the project
  private final Map<String, ImmutableSet<String>> transitiveSourceDeps;
  // All the dependencies of a java rule
  private final Map<String, Set<String>> ruleDeps;
  // All dependencies external to this project
  private final Set<String> projectDeps;

  private final Set<String> androidTargets;

  // Listeners for changes to the build graph
  private final List<BuildGraphListener> listeners;

  public void addListener(BuildGraphListener listener) {
    listeners.add(listener);
  }

  /** Represents a location on a file. */
  public static class Location {

    // TODO does this belong in the open source code? Should it be encapsulated somehow else?
    private static final String READONLY_WORKSPACE = "/workspace/READONLY/google3/";
    private static final Pattern PATTERN = Pattern.compile("(.*):(\\d+):(\\d+)");

    public final String file;
    public final int row;
    public final int column;

    /**
     * @param location A location as provided by bazel, i.e. {@code /path/to/file:lineno:columnno}
     * @param workspaceRoot Absolute path to the workspace root bazel was running in
     */
    public Location(String location, Path workspaceRoot) {
      Matcher matcher = PATTERN.matcher(location);
      Preconditions.checkArgument(matcher.matches(), "Location not recognized: %s", location);
      String file = matcher.group(1);
      if (!file.startsWith(workspaceRoot.toString())) {
        Preconditions.checkArgument(
            file.startsWith(READONLY_WORKSPACE),
            "Path not in workspace not readonly workspace: %s",
            file);
        file = workspaceRoot + "/" + file.substring(READONLY_WORKSPACE.length());
      }
      this.file = file;
      row = Integer.parseInt(matcher.group(2));
      column = Integer.parseInt(matcher.group(3));
    }
  }

  public BuildGraph() {
    locations = new HashMap<>();
    javaSources = new HashSet<>();
    fileToTarget = new HashMap<>();
    sourceOwner = new HashMap<>();
    transitiveSourceDeps = new HashMap<>();
    ruleDeps = new HashMap<>();
    projectDeps = new HashSet<>();
    listeners = new ArrayList<>();
    androidTargets = new HashSet<>();
  }

  public void clear() {
    locations.clear();
    javaSources.clear();
    fileToTarget.clear();
    sourceOwner.clear();
    transitiveSourceDeps.clear();
    ruleDeps.clear();
    projectDeps.clear();
  }

  public void initialize(Path workspaceRoot, BlazeContext context, String protoFile)
      throws IOException {
    clear();
    // At this point the query is done, and we parse the proto output of it.
    // This for AGSA is 1.6m objects.
    context.output(PrintOutput.log("Analyzing project structure..."));

    Set<String> packages = new HashSet<>();
    long now = System.nanoTime();
    try (BufferedInputStream fis = new BufferedInputStream(new FileInputStream(protoFile))) {

      int nTargets = 0;

      // A hacky collection the project state. This is the equivalent of the targetmap data:

      // An aggregation of all the dependencies of java rules
      Set<String> deps = new HashSet<>();
      // All the proto targets
      Set<String> protos = new HashSet<>();
      // Counts of all kinds of rules
      Map<String, Integer> ruleCount = new HashMap<>();
      // All the direct dependencies from source files to things it needs outside the project
      Map<String, Set<String>> sourceDeps = new HashMap<>();
      while (true) {
        Target target = Target.parseDelimitedFrom(fis);
        if (target == null) {
          break;
        }
        if (target.getType() == Discriminator.SOURCE_FILE) {
          Location l = new Location(target.getSourceFile().getLocation(), workspaceRoot);
          if (l.file.endsWith("/BUILD")) {
            packages.add(l.file);
          }
          locations.put(target.getSourceFile().getName(), l);
          String rel = workspaceRoot.relativize(Paths.get(l.file)).toString();
          fileToTarget.put(rel, target.getSourceFile().getName());
        } else if (target.getType() == Discriminator.RULE) {
          ruleCount.compute(target.getRule().getRuleClass(), (k, v) -> (v == null ? 0 : v) + 1);
          if (isJavaRule(target.getRule().getRuleClass())) {
            Set<String> thisSources = new HashSet<>();
            Set<String> thisDeps = new HashSet<>();
            for (Attribute attribute : target.getRule().getAttributeList()) {
              if (attribute.getName().equals("srcs")) {
                for (String s : attribute.getStringListValueList()) {
                  thisSources.add(s);
                }
              } else if (attribute.getName().equals("deps")) {
                for (String s : attribute.getStringListValueList()) {
                  thisDeps.add(s);
                }
              }
            }

            ruleDeps
                .computeIfAbsent(target.getRule().getName(), x -> new HashSet<>())
                .addAll(thisDeps);
            for (String thisSource : thisSources) {
              Set<String> currentDeps = sourceDeps.get(thisSource);
              ruleDeps
                  .computeIfAbsent(thisSource, x -> new HashSet<>())
                  .add(target.getRule().getName());
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
            javaSources.addAll(thisSources);
            deps.addAll(thisDeps);

            if (ANDROID_RULE_TYPES.contains(target.getRule().getRuleClass())) {
              androidTargets.add(target.getRule().getName());
            }
          } else if (target.getRule().getRuleClass().equals("java_lite_proto_library")) {
            protos.add(target.getRule().getName());
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
      // Treat proto deps as external deps
      projectDeps.addAll(protos);

      // Prune the source deps to only external ones.
      // This could be technically incorrect, because if there is a path from an internal rule
      // to an external rule we should consider it too. There is an implementation for it
      // in #transitiveDeps but that is done for one target, we have to do it for all before hand
      // here
      for (Entry<String, Set<String>> entry : sourceDeps.entrySet()) {
        entry.getValue().retainAll(projectDeps);
      }

      long elapsedMs = (System.nanoTime() - now) / 1000000L;
      context.output(
          PrintOutput.log(String.format("Processed %d targets, in %d ms", nTargets, elapsedMs)));

      ArrayList<Entry<String, Integer>> entries = new ArrayList<>(ruleCount.entrySet());
      entries.sort(Entry.<String, Integer>comparingByValue().reversed());
      int shown = 0;
      int limit = 50;
      for (Entry<String, Integer> entry : entries) {
        if (entry.getValue() <= limit) {
          context.output(
              PrintOutput.log(
                  String.format(
                      "[...] truncated %d rules with <= %d count", nTargets - shown, limit)));
          break;
        }
        shown += entry.getValue();
        context.output(PrintOutput.log(String.format("%s: %d", entry.getKey(), entry.getValue())));
      }
      context.output(PrintOutput.log(String.format("Found source files %d", locations.size())));
      context.output(
          PrintOutput.log(String.format("Found %d targets as java sources", javaSources.size())));
      context.output(PrintOutput.log(String.format("Found %d packages", packages.size())));
      context.output(PrintOutput.log(String.format("Found %d dependencies", deps.size())));
      context.output(
          PrintOutput.log(
              String.format("Of which %d are to targets outside the project", projectDeps.size())));

      int maxDeps = 0;
      String worstSource = null;
      for (Entry<String, Set<String>> e : sourceDeps.entrySet()) {
        if (e.getValue().size() > maxDeps) {
          maxDeps = e.getValue().size();
          worstSource = e.getKey();
        }
      }
      context.output(
          PrintOutput.log(String.format("Source with most direct dependencies (%d) is:", maxDeps)));
      context.output(PrintOutput.log(worstSource));
      for (BuildGraphListener listener : listeners) {
        listener.graphCreated(context);
      }
    }
  }

  private static boolean isJavaRule(String ruleClass) {
    return JAVA_RULE_TYPES.contains(ruleClass) || ANDROID_RULE_TYPES.contains(ruleClass);
  }

  /** Recursively get all the transitive deps outside the project */
  private ImmutableSet<String> getTargetDependencies(String target) {
    ImmutableSet<String> transitiveDeps = transitiveSourceDeps.get(target);
    if (transitiveDeps != null) {
      return transitiveDeps;
    }
    ImmutableSet.Builder<String> builder = ImmutableSet.builder();
    // There are no cycles in blaze, so we can recursively call down
    Set<String> immediateDeps = ruleDeps.get(target);
    if (immediateDeps == null) {
      builder.add(target);
    } else {
      for (String dep : immediateDeps) {
        builder.addAll(getTargetDependencies(dep));
      }
    }
    transitiveDeps = Sets.intersection(builder.build(), projectDeps).immutableCopy();
    transitiveSourceDeps.put(target, transitiveDeps);
    return transitiveDeps;
  }

  /**
   * Given a path to a file it returns the target that owns the file. Note that in general there
   * could be multiple targets that compile a file, but we try to choose the smallest one, as it
   * would have everything the file needs to be compiled.
   */
  public String getTargetOwner(String path) {
    String syncTarget = fileToTarget.get(path);
    return sourceOwner.get(syncTarget);
  }

  /**
   * For a given path to a file, returns all the targets outside the project that this file needs to
   * be edited fully.
   */
  @Nullable
  public ImmutableSet<String> getFileDependencies(String rel) {
    String target = fileToTarget.get(rel);
    if (target == null) {
      return null;
    }
    return getTargetDependencies(target);
  }

  /** Returns a list of all the source files of the project. */
  public List<String> getJavaSourceFiles() {
    List<String> files = new ArrayList<>();
    for (String src : javaSources) {
      Location location = locations.get(src);
      if (location == null) {
        continue;
      }
      files.add(location.file);
    }
    return files;
  }

  public List<String> getAllSourceFiles() {
    List<String> files = new ArrayList<>();
    files.addAll(fileToTarget.keySet());
    return files;
  }

  public List<String> getAndroidSourceFiles() {
    List<String> files = new ArrayList<>();
    for (String source : javaSources) {
      String owningTarget = sourceOwner.get(source);
      if (androidTargets.contains(owningTarget)) {
        Location location = locations.get(source);
        if (location == null) {
          continue;
        }
        files.add(location.file);
      }
    }
    return files;
  }

  /** A listener interface for changes made to the build graph. */
  public interface BuildGraphListener {
    void graphCreated(BlazeContext context) throws IOException;
  }
}
