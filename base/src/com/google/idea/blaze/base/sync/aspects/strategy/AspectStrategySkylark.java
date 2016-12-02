/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.sync.aspects.strategy;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.repackaged.devtools.intellij.ideinfo.IntellijIdeInfo;
import com.google.repackaged.protobuf.TextFormat;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/** Aspect strategy for Skylark. */
public class AspectStrategySkylark implements AspectStrategy {

  @Override
  public String getName() {
    return "SkylarkAspect";
  }

  protected String getAspectFlag() {
    return "--aspects=@bazel_tools://tools/ide/intellij_info.bzl%intellij_info_aspect";
  }

  @Override
  public void modifyIdeInfoCommand(BlazeCommand.Builder blazeCommandBuilder) {
    blazeCommandBuilder
        .addBlazeFlags(getAspectFlag())
        .addBlazeFlags("--output_groups=intellij-info-text");
  }

  @Override
  public void modifyIdeResolveCommand(BlazeCommand.Builder blazeCommandBuilder) {
    blazeCommandBuilder
        .addBlazeFlags(getAspectFlag())
        .addBlazeFlags("--output_groups=intellij-resolve");
  }

  @Override
  public String getAspectOutputFileExtension() {
    return ".intellij-info.txt";
  }

  @Override
  public IntellijIdeInfo.TargetIdeInfo readAspectFile(InputStream inputStream) throws IOException {
    IntellijIdeInfo.TargetIdeInfo.Builder builder = IntellijIdeInfo.TargetIdeInfo.newBuilder();
    TextFormat.Parser parser = TextFormat.Parser.newBuilder().setAllowUnknownFields(true).build();
    parser.merge(new InputStreamReader(inputStream, UTF_8), builder);
    return builder.build();
  }
}
