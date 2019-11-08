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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.command.buildresult.BlazeArtifact;
import com.google.idea.blaze.base.model.BlazeVersionData;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.common.experiments.BoolExperiment;
import com.google.protobuf.repackaged.TextFormat;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import javax.annotation.Nullable;

/** Aspect strategy for Skylark. */
public abstract class AspectStrategy {

  public static final Predicate<String> ASPECT_OUTPUT_FILE_PREDICATE =
      str -> str.endsWith(".intellij-info.txt");

  /** A Blaze output group created by the aspect. */
  public enum OutputGroup {
    INFO("intellij-info-"),
    RESOLVE("intellij-resolve-"),
    COMPILE("intellij-compile-");

    public final String prefix;

    OutputGroup(String prefix) {
      this.prefix = prefix;
    }
  }

  public static AspectStrategy getInstance(BlazeVersionData versionData) {
    AspectStrategy strategy =
        AspectStrategyProvider.EP_NAME
            .extensions()
            .map(p -> p.getStrategy(versionData))
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);
    return Preconditions.checkNotNull(strategy);
  }

  /**
   * Whether output groups containing a trimmed build graph can be requested, when relevant.
   *
   * <p>Per-language switching is hard-coded for now.
   */
  private static final BoolExperiment directDepsTrimmingEnabled =
      new BoolExperiment("sync.allow.requesting.direct.deps", true);

  /** True if the aspect available to the plugin supports direct deps trimming. */
  private final boolean aspectSupportsDirectDepsTrimming;

  protected AspectStrategy(boolean aspectSupportsDirectDepsTrimming) {
    this.aspectSupportsDirectDepsTrimming = aspectSupportsDirectDepsTrimming;
  }

  public abstract String getName();

  protected abstract List<String> getAspectFlags();

  /**
   * Add the aspect to the build and request the given {@code OutputGroup}s. This method should only
   * be called once.
   *
   * @param directDepsOnly when supported for a language, the build outputs will be trimmed to
   *     direct deps of the top-level targets.
   */
  public final void addAspectAndOutputGroups(
      BlazeCommand.Builder builder,
      Collection<OutputGroup> outputGroups,
      Set<LanguageClass> activeLanguages,
      boolean directDepsOnly) {
    List<String> groups =
        outputGroups.stream()
            .flatMap(g -> getOutputGroups(g, activeLanguages, directDepsOnly).stream())
            .collect(toImmutableList());
    builder
        .addBlazeFlags(getAspectFlags())
        .addBlazeFlags("--output_groups=" + Joiner.on(',').join(groups));
  }

  /**
   * Get the names of the output groups created by the aspect for the given {@link OutputGroup} and
   * languages.
   */
  public final ImmutableList<String> getOutputGroups(
      OutputGroup outputGroup, Set<LanguageClass> activeLanguages, boolean directDepsOnly) {
    TreeSet<String> outputGroups = new TreeSet<>();
    if (outputGroup.equals(OutputGroup.INFO)) {
      outputGroups.add(outputGroup.prefix + "generic");
    }
    activeLanguages.stream()
        .map(l -> getOutputGroupForLanguage(outputGroup, l, directDepsOnly))
        .filter(Objects::nonNull)
        .forEach(outputGroups::add);
    return ImmutableList.copyOf(outputGroups);
  }

  public final IntellijIdeInfo.TargetIdeInfo readAspectFile(BlazeArtifact file) throws IOException {
    try (InputStream inputStream = file.getInputStream()) {
      IntellijIdeInfo.TargetIdeInfo.Builder builder = IntellijIdeInfo.TargetIdeInfo.newBuilder();
      TextFormat.Parser parser = TextFormat.Parser.newBuilder().setAllowUnknownFields(true).build();
      parser.merge(new InputStreamReader(inputStream, UTF_8), builder);
      return builder.build();
    }
  }

  @Nullable
  private String getOutputGroupForLanguage(
      OutputGroup group, LanguageClass language, boolean directDepsOnly) {
    String langSuffix = getLanguageSuffix(language);
    if (langSuffix == null) {
      return null;
    }
    directDepsOnly = directDepsOnly && allowDirectDepsTrimming(language);
    if (!directDepsOnly) {
      return group.prefix + langSuffix;
    }
    return group.prefix + langSuffix + "-direct-deps";
  }

  @Nullable
  private static String getLanguageSuffix(LanguageClass language) {
    LanguageOutputGroup group = LanguageOutputGroup.forLanguage(language);
    return group != null ? group.suffix : null;
  }

  private boolean allowDirectDepsTrimming(LanguageClass language) {
    return aspectSupportsDirectDepsTrimming
        && directDepsTrimmingEnabled.getValue()
        && language != LanguageClass.C;
  }
}
