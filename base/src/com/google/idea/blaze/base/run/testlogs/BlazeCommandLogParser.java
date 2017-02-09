/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.run.testlogs;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.model.primitives.Label;
import com.intellij.openapi.diagnostic.Logger;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;

/** Parses the list of test targets run from the bazel command log */
public class BlazeCommandLogParser {

  private static final Logger logger = Logger.getInstance(BlazeCommandLogParser.class);

  private static final Pattern TEST_LOG = Pattern.compile("^(//[^\\s]*) .*? (PASSED|FAILED)");

  /** Finds log location and target label for all tests listed in the master log. */
  public static ImmutableSet<Label> parseTestTargets(File commandLog) {
    try (Stream<String> stream = Files.lines(Paths.get(commandLog.getPath()))) {
      return parseTestTargets(stream);
    } catch (IOException e) {
      logger.warn("Error parsing master log", e);
      return ImmutableSet.of();
    }
  }

  @VisibleForTesting
  static ImmutableSet<Label> parseTestTargets(Stream<String> lines) {
    return ImmutableSet.copyOf(
        lines
            .map(BlazeCommandLogParser::parseTestTarget)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet()));
  }

  @Nullable
  @VisibleForTesting
  static Label parseTestTarget(String line) {
    Matcher match = TEST_LOG.matcher(line);
    if (!match.find()) {
      return null;
    }
    return Label.createIfValid(match.group(1));
  }
}
