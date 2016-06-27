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
package com.google.idea.blaze.base.sync.aspects;

import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.repackaged.devtools.build.lib.ideinfo.androidstudio.AndroidStudioIdeInfo;
import com.google.repackaged.protobuf.TextFormat;

import java.io.*;

/**
 * Indirection for our various ways of calling the aspect.
 */
public interface AspectStrategy {

  String getName();

  void modifyIdeInfoCommand(BlazeCommand.Builder blazeCommandBuilder);

  void modifyIdeResolveCommand(BlazeCommand.Builder blazeCommandBuilder);

  String getAspectOutputFileExtension();

  AndroidStudioIdeInfo.RuleIdeInfo readAspectFile(File file) throws IOException;

  AspectStrategy NATIVE_ASPECT = new AspectStrategy() {
    @Override
    public String getName() {
      return "NativeAspect";
    }

    @Override
    public void modifyIdeInfoCommand(BlazeCommand.Builder blazeCommandBuilder) {
      blazeCommandBuilder
        .addBlazeFlags("--aspects=AndroidStudioInfoAspect")
        .addBlazeFlags("--output_groups=ide-info");
    }

    @Override
    public void modifyIdeResolveCommand(BlazeCommand.Builder blazeCommandBuilder) {
      blazeCommandBuilder
        .addBlazeFlags("--aspects=AndroidStudioInfoAspect")
        .addBlazeFlags("--output_groups=ide-resolve");
    }

    @Override
    public String getAspectOutputFileExtension() {
      return ".aswb-build";
    }

    @Override
    public AndroidStudioIdeInfo.RuleIdeInfo readAspectFile(File file) throws IOException {
      try (InputStream inputStream = new FileInputStream(file)) {
        return AndroidStudioIdeInfo.RuleIdeInfo.parseFrom(inputStream);
      }
    }
  };

  AspectStrategy SKYLARK_ASPECT = new AspectStrategy() {
    @Override
    public String getName() {
      return "SkylarkAspect";
    }

    private void addAspectFlag(BlazeCommand.Builder blazeCommandBuilder) {
      blazeCommandBuilder.addBlazeFlags(
        "--aspects=//third_party/bazel/src/test/java/com/google/devtools/build/lib/ideinfo/intellij_info.bzl%intellij_info_aspect"
      );
    }

    @Override
    public void modifyIdeInfoCommand(BlazeCommand.Builder blazeCommandBuilder) {
      addAspectFlag(blazeCommandBuilder);
      blazeCommandBuilder.addBlazeFlags("--output_groups=ide-info-text");
    }

    @Override
    public void modifyIdeResolveCommand(BlazeCommand.Builder blazeCommandBuilder) {
      addAspectFlag(blazeCommandBuilder);
      blazeCommandBuilder.addBlazeFlags("--output_groups=ide-resolve");
    }

    @Override
    public String getAspectOutputFileExtension() {
      return ".intellij-build.txt";
    }

    @Override
    public AndroidStudioIdeInfo.RuleIdeInfo readAspectFile(File file) throws IOException {
      try (InputStream inputStream = new FileInputStream(file)) {
        AndroidStudioIdeInfo.RuleIdeInfo.Builder builder = AndroidStudioIdeInfo.RuleIdeInfo.newBuilder();
        TextFormat.Parser parser = TextFormat.Parser.newBuilder()
          .setAllowUnknownFields(true)
          .build();
        parser.merge(new InputStreamReader(inputStream), builder);
        return builder.build();
      }
    }
  };

}
