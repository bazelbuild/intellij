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
import com.intellij.openapi.diagnostic.Logger;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.annotation.Nullable;

/** Parses an individual test target's log file produced by blaze. */
public class BlazeTestLogParser {

  private static final Logger logger = Logger.getInstance(BlazeTestLogParser.class);

  private static final Pattern XML_LOCATION = Pattern.compile("XML_OUTPUT_FILE=([^\\s]*)");

  /** Finds log location and target label for all tests listed in the master log. */
  @Nullable
  public static File findTestXmlFile(File testLog) {
    try (Stream<String> stream = Files.lines(Paths.get(testLog.getPath()))) {
      return parseTestXmlFile(stream);
    } catch (IOException e) {
      logger.warn("Error parsing test log", e);
      return null;
    }
  }

  @Nullable
  @VisibleForTesting
  static File parseTestXmlFile(Stream<String> stream) {
    return stream
        .map(BlazeTestLogParser::parseXmlLocation)
        .filter(Objects::nonNull)
        .findFirst()
        .orElse(null);
  }

  @Nullable
  @VisibleForTesting
  static File parseXmlLocation(String line) {
    Matcher match = XML_LOCATION.matcher(line);
    if (!match.find()) {
      return null;
    }
    return new File(match.group(1));
  }
}
