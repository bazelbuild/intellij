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

import com.google.devtools.build.lib.query2.proto.proto2api.Build.Attribute;
import com.google.devtools.build.lib.query2.proto.proto2api.Build.Target;
import com.google.devtools.build.lib.query2.proto.proto2api.Build.Target.Discriminator;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.PrintOutput;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
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

  // A map from target to file on disk for all source files
  private final Map<String, Location> locations;
  // A set of all the targets that show up in java rules 'src' attributes
  private final Set<String> javaSources;
  // A map from a file path to its target
  private final Map<String, String> fileToTarget;
  // From source target to the rule that builds it. If multiple one is picked.
  private final Map<String, String> sourceOwner;
  // All the dependencies from source files to things it needs outside the project
  private final Map<String, Set<String>> transitiveSourceDeps;
  // All the dependencies of a java rule
  private final Map<String, Set<String>> ruleDeps;
  // All dependencies external to this project
  private final Set<String> projectDeps;

  // Listeners for changes to the build graph
  private final List<BuildGraphListener> listeners;

  public void addListener(BuildGraphListener listener) {
    listeners.add(listener);
  }

  /** Represents a location on a file. */
  public static class Location {
    private static final Pattern PATTERN = Pattern.compile("(.*):(\\d+):(\\d+)");
    public String file;
    public int row;
    public int column;

    public Location(String full) {
      Matcher matcher = PATTERN.matcher(full);
      if (matcher.matches()) {
        file = matcher.group(1);
        row = Integer.parseInt(matcher.group(2));
        column = Integer.parseInt(matcher.group(3));
      }
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

  public void initialize(WorkspaceRoot root, BlazeContext context, String protoFile)
      throws IOException {
    clear();
    // At this point the query is done, and we parse the proto output of it.
    // This for AGSA is 1.6m objects.
    Map<String, List<String>> packages = new HashMap<>();
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
          Location l = new Location(target.getSourceFile().getLocation());
          if (l.file.endsWith("/BUILD")) {
            packages.computeIfAbsent(l.file, x -> new ArrayList<>());
          }
          locations.put(target.getSourceFile().getName(), l);
          String rel = root.workspacePathFor(new File(l.file)).toString();
          fileToTarget.put(rel, target.getSourceFile().getName());
        } else if (target.getType() == Discriminator.RULE) {
          ruleCount.compute(target.getRule().getRuleClass(), (k, v) -> (v == null ? 0 : v) + 1);
          if (target.getRule().getRuleClass().equals("java_library")
              || target.getRule().getRuleClass().equals("android_library")
              || target.getRule().getRuleClass().equals("java_binary")
              || target.getRule().getRuleClass().equals("android_binary")
              || target.getRule().getRuleClass().equals("android_local_test")
              || target.getRule().getRuleClass().equals("android_instrumentation_test")
              || target.getRule().getRuleClass().equals("kt_jvm_library_helper")
              || target.getRule().getRuleClass().equals("kt_android_library_helper")
              || target.getRule().getRuleClass().equals("java_test")) {
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

  /** Recursively get all the transitive deps outside the project */
  private Set<String> getTargetDependencies(String target) {
    Set<String> val = transitiveSourceDeps.get(target);
    if (val != null) {
      return val;
    }
    Set<String> ret = new HashSet<>();
    // There are no cycles in blaze, so we can recursively call down
    Set<String> deps = ruleDeps.get(target);
    if (deps == null) {
      ret.add(target);
    } else {
      for (String dep : deps) {
        ret.addAll(getTargetDependencies(dep));
      }
    }
    ret.removeIf(x -> !projectDeps.contains(x));
    transitiveSourceDeps.put(target, ret);
    return ret;
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
  public Set<String> getFileDependencies(String rel) {
    String target = fileToTarget.get(rel);
    if (target == null) {
      return null;
    }
    return getTargetDependencies(target);
  }

  /** Returns a list of all the source files of the project. */
  public List<String> getSourceFiles() {
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

  /** A listener interface for changes made to the build graph. */
  public interface BuildGraphListener {
    void graphCreated(BlazeContext context) throws IOException;
  }
}
