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
package com.google.idea.blaze.java.fastbuild;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.joining;

import com.google.devtools.intellij.aspect.FastBuildInfo;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.model.BlazeVersionData;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.google.idea.blaze.base.util.BuildSystemExtensionPoint;
import com.google.protobuf.TextFormat;
import com.intellij.openapi.extensions.ExtensionPointName;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

abstract class FastBuildAspectStrategy implements BuildSystemExtensionPoint {

  private static final ExtensionPointName<FastBuildAspectStrategy> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.FastBuildAspectStrategy");

  private static final Predicate<String> OUTPUT_FILE_PREDICATE =
      str ->
          str.endsWith(".ide-fast-build-info.txt") || str.endsWith(".ide-fast-build-info.txt.gz");
  private static final String OUTPUT_GROUP = "ide-fast-build";

  static FastBuildAspectStrategy getInstance(BuildSystemName buildSystemName) {
    return BuildSystemExtensionPoint.getInstance(EP_NAME, buildSystemName);
  }

  protected abstract List<String> getAspectFlags(BlazeVersionData versionData);

  final Predicate<String> getAspectOutputFilePredicate() {
    return OUTPUT_FILE_PREDICATE;
  }

  final String getAspectOutputGroup() {
    return OUTPUT_GROUP;
  }

  /**
   * Add the aspect to the build and request the given {@code outputGroups} in addition to those
   * provided by the aspect. This method should only be called once.
   */
  final void addAspectAndOutputGroups(
      BlazeCommand.Builder blazeCommandBuilder,
      BlazeVersionData versionData,
      String... additionalOutputGroups) {
    String outputGroups =
        Stream.concat(Arrays.stream(additionalOutputGroups), Stream.of(OUTPUT_GROUP))
            .collect(joining(","));
    blazeCommandBuilder
        .addBlazeFlags(getAspectFlags(versionData))
        .addBlazeFlags("--output_groups=" + outputGroups);
  }

  FastBuildBlazeData readFastBuildBlazeData(File file) {
    try (Reader reader = getAspectOutputReader(file)) {
      FastBuildInfo.FastBuildBlazeData.Builder builder =
          FastBuildInfo.FastBuildBlazeData.newBuilder();
      TextFormat.Parser parser = TextFormat.Parser.newBuilder().setAllowUnknownFields(true).build();
      parser.merge(reader, builder);
      return FastBuildBlazeData.fromProto(builder.build());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static Reader getAspectOutputReader(File file) throws IOException {
    InputStream inputStream = new BufferedInputStream(new FileInputStream(file));
    if (file.getName().endsWith(".gz")) {
      inputStream = new GZIPInputStream(inputStream);
    }
    return new InputStreamReader(inputStream, UTF_8);
  }
}
