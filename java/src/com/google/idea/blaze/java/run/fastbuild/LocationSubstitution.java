/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.java.run.fastbuild;

import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.model.primitives.Label;
import com.intellij.execution.ExecutionException;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

// TODO(b/118741281): have Blaze perform the substitution in the aspect
final class LocationSubstitution {

  // The unescaped regex:
  //   \$\((locations?)\s+([^)]+)\s*\)
  // The first group is 'location' or 'locations'. The second group is the target.
  private static final Pattern LOCATION_PATTERN =
      Pattern.compile("\\$\\((locations?)\\s+([^)]+)\\s*\\)");

  private LocationSubstitution() {}

  static String replaceLocations(
      String input, Label target, Map<Label, ? extends Set<ArtifactLocation>> targetData)
      throws ExecutionException {

    Matcher matcher = LOCATION_PATTERN.matcher(input);
    StringBuffer result = new StringBuffer();
    while (matcher.find()) {

      Label label = getLabelFromLocation(matcher.group(2), target);
      Set<ArtifactLocation> artifacts = getArtifacts(label, target, targetData, matcher);
      matcher.appendReplacement(
          result,
          artifacts.stream()
              .map(ArtifactLocation::getRelativePath)
              .collect(Collectors.joining(" ")));
    }
    matcher.appendTail(result);
    return result.toString();
  }

  private static Label getLabelFromLocation(String location, Label target)
      throws ExecutionException {
    int colonIndex = location.indexOf(':');
    if (colonIndex == 0 && location.length() > 1) {
      // A relative target like ":foo"
      return target.withTargetName(location.substring(1));
    } else if (colonIndex > 0) {
      // A fully-specified location like "//java:foo
      return Label.create(location);
    } else if (!location.startsWith("//")) {
      // A relative path to a file (like "files/hello.txt")
      // or a BUILD rule in the same package (like "myfiles", which is the same as ":myfiles")
      return target.withTargetName(location);
    }

    // An absolute path with no colon like "//devtools/ide"
    int lastSlash = location.lastIndexOf('/');
    if (lastSlash == location.length() - 1) {
      throw new ExecutionException(error(target, "invalid target format '" + location + "'"));
    }

    // Turn a label like //devtools/ide into //devtools/ide:ide
    String implicitTarget = location.substring(lastSlash + 1);
    return Label.create(location + ':' + implicitTarget);
  }

  private static Set<ArtifactLocation> getArtifacts(
      Label label,
      Label target,
      Map<Label, ? extends Set<ArtifactLocation>> targetData,
      Matcher matcher)
      throws ExecutionException {

    if (!targetData.containsKey(label)) {
      throw new ExecutionException(
          error(target, "couldn't find label " + label + " in 'data' attribute."));
    }

    Set<ArtifactLocation> artifacts = targetData.get(label);

    if (artifacts.isEmpty()) {
      throw new ExecutionException(error(target, "target " + label + " has no outputs."));
    } else if (!allowsMultipleArtifacts(matcher) && artifacts.size() > 1) {
      // For some targets (like java_libraries) Blaze presents multiple artifacts to us, whereas
      // it only sees one. This will definitely break in that case, but I can't see any way to
      // decide which artifact is the 'real' one.
      throw new ExecutionException(error(target, "target " + label + " has multiple outputs."));
    }

    return artifacts;
  }

  private static boolean allowsMultipleArtifacts(Matcher matcher) {
    return matcher.group(1).equals("locations");
  }

  private static String error(Label target, String error) {
    return "Error in $(location) substitution in " + target + ": " + error;
  }
}
