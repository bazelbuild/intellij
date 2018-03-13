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

import com.google.common.base.Joiner;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.command.BlazeCommand.Builder;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.common.experiments.BoolExperiment;
import com.google.protobuf.repackaged.TextFormat;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Aspect strategy for Skylark. */
public abstract class AspectStrategy {

  private static final BoolExperiment usePerLanguageOutputGroups =
      new BoolExperiment("blaze.use.per.language.output.groups", true);

  public abstract String getName();

  protected abstract List<String> getAspectFlags();

  protected abstract boolean hasPerLanguageOutputGroups();

  private boolean usePerLanguageOutputGroups() {
    return usePerLanguageOutputGroups.getValue() && hasPerLanguageOutputGroups();
  }

  public final void modifyIdeInfoCommand(
      BlazeCommand.Builder blazeCommandBuilder, Set<LanguageClass> activeLanguages) {
    blazeCommandBuilder.addBlazeFlags(getAspectFlags());
    if (!usePerLanguageOutputGroups()) {
      blazeCommandBuilder.addBlazeFlags("--output_groups=intellij-info-text");
      return;
    }
    List<String> outputGroups = getOutputGroups(activeLanguages, "intellij-info-");
    outputGroups.add("intellij-info-generic");
    String flag = "--output_groups=" + Joiner.on(',').join(outputGroups);
    blazeCommandBuilder.addBlazeFlags(flag);
  }

  public final void modifyIdeResolveCommand(
      BlazeCommand.Builder blazeCommandBuilder, Set<LanguageClass> activeLanguages) {
    blazeCommandBuilder.addBlazeFlags(getAspectFlags());
    if (!usePerLanguageOutputGroups()) {
      blazeCommandBuilder.addBlazeFlags("--output_groups=intellij-resolve");
      return;
    }
    List<String> outputGroups = getOutputGroups(activeLanguages, "intellij-resolve-");
    String flag = "--output_groups=" + Joiner.on(',').join(outputGroups);
    blazeCommandBuilder.addBlazeFlags(flag);
  }

  public final void modifyIdeCompileCommand(
      Builder blazeCommandBuilder, Set<LanguageClass> activeLanguages) {
    blazeCommandBuilder.addBlazeFlags(getAspectFlags());
    if (!usePerLanguageOutputGroups()) {
      blazeCommandBuilder.addBlazeFlags("--output_groups=intellij-compile");
      return;
    }
    List<String> outputGroups = getOutputGroups(activeLanguages, "intellij-compile-");
    String flag = "--output_groups=" + Joiner.on(',').join(outputGroups);
    blazeCommandBuilder.addBlazeFlags(flag);
  }

  private static List<String> getOutputGroups(Set<LanguageClass> activeLanguages, String prefix) {
    return activeLanguages
        .stream()
        .map(LanguageOutputGroup::forLanguage)
        .filter(Objects::nonNull)
        .map(lang -> prefix + lang.suffix)
        .distinct()
        .sorted()
        .collect(Collectors.toList());
  }

  public final String getAspectOutputFileExtension() {
    return ".intellij-info.txt";
  }

  public final IntellijIdeInfo.TargetIdeInfo readAspectFile(InputStream inputStream)
      throws IOException {
    IntellijIdeInfo.TargetIdeInfo.Builder builder = IntellijIdeInfo.TargetIdeInfo.newBuilder();
    TextFormat.Parser parser = TextFormat.Parser.newBuilder().setAllowUnknownFields(true).build();
    parser.merge(new InputStreamReader(inputStream, UTF_8), builder);
    return builder.build();
  }
}
