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
import com.google.common.collect.ImmutableList;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.settings.BuildSystem;
import com.google.idea.blaze.base.util.BuildSystemExtensionPoint;
import com.google.protobuf.repackaged.TextFormat;
import com.intellij.openapi.extensions.ExtensionPointName;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.zip.GZIPInputStream;

/** Aspect strategy for Skylark. */
public abstract class AspectStrategy implements BuildSystemExtensionPoint {

  private static final ExtensionPointName<AspectStrategy> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.AspectStrategy");

  private static final Predicate<String> ASPECT_OUTPUT_FILE_PREDICATE =
      str -> str.endsWith(".intellij-info.txt") || str.endsWith(".intellij-info.txt.gz");

  /** A Blaze output group created by the aspect. */
  public enum OutputGroup {
    INFO("intellij-info-"),
    RESOLVE("intellij-resolve-"),
    COMPILE("intellij-compile-");

    private final String prefix;

    OutputGroup(String prefix) {
      this.prefix = prefix;
    }
  }

  public static AspectStrategy getInstance(BuildSystem buildSystem) {
    return BuildSystemExtensionPoint.getInstance(EP_NAME, buildSystem);
  }

  public abstract String getName();

  protected abstract List<String> getAspectFlags();

  /**
   * Add the aspect to the build and request the given {@code OutputGroup}. This method should only
   * be called once.
   */
  public final void addAspectAndOutputGroups(
      BlazeCommand.Builder blazeCommandBuilder,
      OutputGroup outputGroup,
      Set<LanguageClass> activeLanguages) {
    addAspectAndOutputGroups(blazeCommandBuilder, getOutputGroups(outputGroup, activeLanguages));
  }

  /**
   * Add the aspect to the build and request the given {@code outputGroups}. This method should only
   * be called once.
   */
  public final void addAspectAndOutputGroups(
      BlazeCommand.Builder blazeCommandBuilder, Collection<String> outputGroups) {
    blazeCommandBuilder
        .addBlazeFlags(getAspectFlags())
        .addBlazeFlags("--output_groups=" + Joiner.on(',').join(outputGroups));
  }

  /**
   * Get the names of the output groups created by the aspect for the given {@link OutputGroup} and
   * languages.
   */
  public final ImmutableList<String> getOutputGroups(
      OutputGroup outputGroup, Set<LanguageClass> activeLanguages) {
    TreeSet<String> outputGroups = new TreeSet<>();
    if (outputGroup.equals(OutputGroup.INFO)) {
      outputGroups.add(outputGroup.prefix + "generic");
    }
    for (LanguageClass langClass : activeLanguages) {
      LanguageOutputGroup currentGroup = LanguageOutputGroup.forLanguage(langClass);
      if (currentGroup != null) {
        outputGroups.add(outputGroup.prefix + currentGroup.suffix);
      }
    }
    return ImmutableList.copyOf(outputGroups);
  }

  public final Predicate<String> getAspectOutputFilePredicate() {
    return ASPECT_OUTPUT_FILE_PREDICATE;
  }

  public final IntellijIdeInfo.TargetIdeInfo readAspectFile(File file) throws IOException {
    try (InputStream inputStream = getAspectInputStream(file)) {
      IntellijIdeInfo.TargetIdeInfo.Builder builder = IntellijIdeInfo.TargetIdeInfo.newBuilder();
      TextFormat.Parser parser = TextFormat.Parser.newBuilder().setAllowUnknownFields(true).build();
      parser.merge(new InputStreamReader(inputStream, UTF_8), builder);
      return builder.build();
    }
  }

  private static InputStream getAspectInputStream(File file) throws IOException {
    InputStream inputStream = new BufferedInputStream(new FileInputStream(file));
    if (file.getName().endsWith(".gz")) {
      inputStream = new GZIPInputStream(inputStream);
    }
    return inputStream;
  }
}
