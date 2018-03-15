/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.aspect;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.devtools.intellij.IntellijAspectTestFixtureOuterClass.IntellijAspectTestFixture;
import com.google.devtools.intellij.IntellijAspectTestFixtureOuterClass.OutputGroup;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo.TargetIdeInfo;
import com.google.protobuf.TextFormat;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/** Combines a bunch of ide infos into an test fixture. */
public class IntellijAspectTestFixtureBuilder {
  public static void main(String[] args) {
    try {
      String paramsFile = args[0].substring(1);
      String fileContents = new String(Files.readAllBytes(Paths.get(paramsFile)), UTF_8);
      List<String> realArgs = Splitter.on('\n').splitToList(fileContents);

      IntellijAspectTestFixture.Builder builder = IntellijAspectTestFixture.newBuilder();
      String outputFilePath = realArgs.get(0);
      String[] aspectFiles = realArgs.get(1).split(":");
      builder.addAllTargets(parseTargets(aspectFiles));
      for (int i = 2; i < realArgs.size(); i += 2) {
        String name = realArgs.get(i);
        List<String> paths = Splitter.on(':').omitEmptyStrings().splitToList(realArgs.get(i + 1));

        builder.addOutputGroups(
            OutputGroup.newBuilder().setName(name).addAllFilePaths(paths).build());
      }

      IntellijAspectTestFixture result = builder.build();
      try (OutputStream outputStream = new FileOutputStream(outputFilePath)) {
        outputStream.write(result.toByteArray());
      }
    } catch (IOException e) {
      System.err.println(e.toString());
      System.exit(-1);
    }
  }

  private static List<TargetIdeInfo> parseTargets(String[] aspectFilePaths) throws IOException {
    ImmutableList.Builder<TargetIdeInfo> targets = ImmutableList.builder();
    for (String path : aspectFilePaths) {
      targets.add(readAspectFile(new File(path)));
    }
    return targets.build();
  }

  private static TargetIdeInfo readAspectFile(File file) throws IOException {
    try (InputStream inputStream = new FileInputStream(file)) {
      TargetIdeInfo.Builder builder = TargetIdeInfo.newBuilder();
      TextFormat.Parser parser = TextFormat.Parser.newBuilder().build();
      parser.merge(new InputStreamReader(inputStream, UTF_8), builder);
      return builder.build();
    }
  }
}
