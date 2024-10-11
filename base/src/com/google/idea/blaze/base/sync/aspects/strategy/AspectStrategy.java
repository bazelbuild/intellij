/*
 * Copyright 2016-2024 The Bazel Authors. All rights reserved.
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
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.model.BlazeVersionData;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.sync.codegenerator.CodeGeneratorRuleNameHelper;
import com.google.idea.blaze.common.artifact.BlazeArtifact;
import com.google.idea.common.experiments.BoolExperiment;
import com.google.protobuf.TextFormat;
import com.intellij.openapi.diagnostic.Logger;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/** Aspect strategy for Skylark. */
public abstract class AspectStrategy {

  private static final Logger logger = Logger.getInstance(AspectStrategy.class);

  /**
   * This template is for an Aspect attr name that is used to provide to the aspect a list
   * of Bazel rule names that are code-generators for a specific language.
   */

  private final static String FORMAT_CODE_GENERATOR_RULE_NAMES_ATTR_NAME
      = "%s_code_generator_rule_names";

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

  protected abstract Optional<String> getAspectFlag();

  protected abstract Boolean supportsAspectsParameters();

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
      boolean directDepsOnly,
      ProjectViewSet viewSet) {

    List<String> groups =
        outputGroups.stream()
            .flatMap(g -> getOutputGroups(g, activeLanguages, directDepsOnly).stream())
            .collect(toImmutableList());
    builder
        .addBlazeFlags(getAspectFlag().map(List::of).orElse(List.of()))
        .addBlazeFlags("--output_groups=" + Joiner.on(',').join(groups));

    List<AspectParameter> codeGeneratorAspectParameters = activeLanguages.stream()
        .map(l -> tryDeriveActionEnvForCodeGeneratorTargetNames(viewSet, l))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .collect(Collectors.toUnmodifiableList());

    if (!codeGeneratorAspectParameters.isEmpty()) {
      if (supportsAspectsParameters()) {
        codeGeneratorAspectParameters.stream()
            .map(AspectParameter::toFlag)
            .forEach(builder::addBlazeFlags);
      } else {
        logger.warn("code generator aspect parameters are required but not supported");
      }
    }
  }

  /**
   * <p>For the given language, this method will retrieve the configured set of Rule
   * names that represent code-generators for the language. It will then turn this
   * into a key-value pair that can be provided to the Bazel aspect so that the
   * aspect knows the set of rule names to assume are code-generators.</p>
   *
   * <p>The key will be something like
   * <code>python_code_generator_rule_names</code> and the value would be
   * something like <code>my_rule_a,my_rule_b</code>.</p>
   */

  private static Optional<AspectParameter> tryDeriveActionEnvForCodeGeneratorTargetNames(
      ProjectViewSet viewSet,
      LanguageClass languageClass) {

    Collection<String> ruleNames = CodeGeneratorRuleNameHelper.deriveRuleNames(viewSet, languageClass);

    if (ruleNames.isEmpty()) {
      return Optional.empty();
    }

    // Do a check here to make sure that no invalid rule names have entered the system. This should
    // have been checked at the point of supply (see PythonCodeGeneratorRuleNamesSectionParser) but
    // to be sure in case the code flows change later.

    for (String ruleName : ruleNames) {
      if (!CodeGeneratorRuleNameHelper.isValidRuleName(ruleName)) {
        throw new IllegalStateException("the rule name [" + ruleName + "] is invalid");
      }
    }

    return Optional.of(new AspectParameter(
        String.format(FORMAT_CODE_GENERATOR_RULE_NAMES_ATTR_NAME, languageClass.getName().toLowerCase()),
        String.join(",", ruleNames)
    ));
  }

  /**
   * Collects the names of output groups created by the aspect and by registered {@link
   * OutputGroupsProvider} extensions for the given {@link OutputGroup} and languages.
   *
   * <p>Delegates to {@link #getBaseOutputGroups(OutputGroup, Set, boolean)}, and {@link
   * #getAdditionalOutputGroups(OutputGroup, Set)}
   */
  private ImmutableList<String> getOutputGroups(
      OutputGroup outputGroup, Set<LanguageClass> activeLanguages, boolean directDepsOnly) {
    TreeSet<String> outputGroups = new TreeSet<>();

    outputGroups.addAll(getBaseOutputGroups(outputGroup, activeLanguages, directDepsOnly));
    outputGroups.addAll(getAdditionalOutputGroups(outputGroup, activeLanguages));

    return ImmutableList.copyOf(outputGroups);
  }

  /**
   * Get the names of the output groups created by the aspect for the given {@link OutputGroup} and
   * languages.
   */
  @VisibleForTesting
  public final ImmutableList<String> getBaseOutputGroups(
      OutputGroup outputGroup, Set<LanguageClass> activeLanguages, boolean directDepsOnly) {
    ImmutableList.Builder<String> outputGroupsBuilder = ImmutableList.builder();
    if (outputGroup.equals(OutputGroup.INFO)) {
      outputGroupsBuilder.add(outputGroup.prefix + "generic");
    }
    activeLanguages.stream()
        .map(l -> getOutputGroupForLanguage(outputGroup, l, directDepsOnly))
        .filter(Objects::nonNull)
        .forEach(outputGroupsBuilder::add);
    return outputGroupsBuilder.build();
  }

  /** Collects the names of output groups from registered {@link OutputGroupsProvider} extensions */
  private static ImmutableList<String> getAdditionalOutputGroups(
      OutputGroup outputGroup, Set<LanguageClass> activeLanguages) {
    return OutputGroupsProvider.EP_NAME
        .extensions()
        .flatMap(
            p ->
                p
                    .getAdditionalOutputGroups(outputGroup, ImmutableSet.copyOf(activeLanguages))
                    .stream())
        .filter(Objects::nonNull)
        .collect(ImmutableList.toImmutableList());
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
        && language != LanguageClass.C
        && language != LanguageClass.GO;
  }

  /**
   * This class models a Bazel aspect `attr` parameter value. It is passed to Bazel as a command
   * line parameter.
   */

  private final static class AspectParameter {
    private final String name;
    private final String value;

    public AspectParameter(String name, String value) {
      Preconditions.checkArgument(!Strings.isNullOrEmpty(name), "name is required");
      Preconditions.checkArgument(!Strings.isNullOrEmpty(value), "value is required");
      this.name = name;
      this.value = value;
    }

    public String getName() {
      return name;
    }

    public String getValue() {
      return value;
    }

    public String toFlag() {
      return String.format("--aspects_parameters=%s=%s", name, value);
    }

    @Override
    public String toString() {
      return toFlag();
    }
  }

}
