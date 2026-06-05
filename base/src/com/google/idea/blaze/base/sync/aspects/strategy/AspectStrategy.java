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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.sync.aspects.storage.AspectWriter;
import com.google.idea.blaze.common.artifact.BlazeArtifact;
import com.google.protobuf.TextFormat;
import com.intellij.openapi.project.Project;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Aspect strategy for Skylark.
 */
public abstract class AspectStrategy {

  public static final Predicate<String> ASPECT_OUTPUT_FILE_PREDICATE =
      str -> str.endsWith(".intellij-info.txt");

  /**
   * A Blaze output group created by the aspect.
   */
  public enum OutputGroup {
    INFO("intellij-info"),
    RESOLVE("intellij-resolve"),
    COMPILE("intellij-compile");

    /**
     * The base name shared by all output groups of this kind. It is also the prefix used to match
     * the kind's groups in the build result: it matches both the language-agnostic group.
     */
    public final String prefix;

    OutputGroup(String prefix) {
      this.prefix = prefix;
    }

    /** The per-language output group name for this kind, e.g. {@code "intellij-info-cpp"}. */
    public String outputGroupForLanguage(LanguageOutputGroup language) {
      return prefix + "-" + language.suffix;
    }
  }

  /**
   * Returns the single {@link AspectStrategy} selected by the registered {@link AspectStrategyProvider} extensions. The
   * providers are mutually exclusive (see {@code bazel.sync.use.intellij.aspect}), so exactly one returns a non-null
   * strategy.
   */
  public static AspectStrategy getInstance() {
    AspectStrategy strategy =
        AspectStrategyProvider.EP_NAME
            .getExtensionList()
            .stream()
            .map(AspectStrategyProvider::getStrategy)
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);
    return Preconditions.checkNotNull(strategy);
  }

  public abstract String getName();

  /**
   * Relative path, under the aspect directory, where this strategy deploys its files.
   */
  public abstract Path prefix();

  /**
   * The writers that materialize this strategy's aspect files into {@code dir.resolve(prefix())}.
   */
  public abstract List<AspectWriter> writers();

  /**
   * Resolves a file produced by this strategy (relative to its deployed {@link #prefix()} directory) into a Bazel
   * {@link Label}, or empty if the file does not exist.
   */
  public abstract Optional<Label> resolve(Project project, String relativePath);

  protected abstract Optional<String> getAspectFlag(Project project, Set<LanguageClass> activeLanguages);

  /**
   * The name of the language-agnostic ("generic") output group for the given {@link OutputGroup}, or
   * empty if the kind has no such group. This is the only part of output group naming that differs
   * between aspect strategies.
   */
  protected abstract Optional<String> genericOutputGroup(OutputGroup outputGroup);

  /**
   * Collects the names of output groups created by the aspect for the given {@link OutputGroup} and languages.
   */
  protected final ImmutableList<String> getOutputGroups(OutputGroup outputGroup, Set<LanguageClass> activeLanguages) {
    final var builder = ImmutableList.<String>builder();
    genericOutputGroup(outputGroup).ifPresent(builder::add);

    activeLanguages.stream()
        .map(LanguageOutputGroup::forLanguage)
        .filter(Objects::nonNull)
        .distinct()
        .map(outputGroup::outputGroupForLanguage)
        .forEach(builder::add);

    return builder.build();
  }

  /**
   * Add the aspect to the build and request the given {@code OutputGroup}s. This method should only be called once.
   */
  public final void addAspectAndOutputGroups(
      Project project,
      BlazeCommand.Builder builder,
      Collection<OutputGroup> outputGroups,
      Set<LanguageClass> activeLanguages) {
    final var groups = outputGroups.stream()
        .flatMap(g -> getOutputGroups(g, activeLanguages).stream())
        .distinct()
        .collect(toImmutableList());

    builder
        .addBlazeFlags(getAspectFlag(project, activeLanguages).map(List::of).orElse(List.of()))
        .addBlazeFlags("--output_groups=" + Joiner.on(',').join(groups));
  }

  public final IntellijIdeInfo.TargetIdeInfo readAspectFile(BlazeArtifact file) throws IOException {
    try (InputStream inputStream = file.getInputStream()) {
      IntellijIdeInfo.TargetIdeInfo.Builder builder = IntellijIdeInfo.TargetIdeInfo.newBuilder();
      TextFormat.Parser parser = TextFormat.Parser.newBuilder().setAllowUnknownFields(true).build();
      parser.merge(new InputStreamReader(inputStream, UTF_8), builder);
      return builder.build();
    }
  }
}
