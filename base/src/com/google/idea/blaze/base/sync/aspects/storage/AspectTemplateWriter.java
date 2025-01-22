/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.sync.aspects.storage;

import static com.google.idea.blaze.base.sync.aspects.storage.AspectRepositoryProvider.ASPECT_TEMPLATE_DIRECTORY;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.ExternalWorkspaceDataManager;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.sync.SyncScope.SyncFailedException;
import com.google.idea.blaze.base.sync.codegenerator.CodeGeneratorRuleNameHelper;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.base.util.TemplateWriter;
import com.intellij.openapi.project.Project;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;

public class AspectTemplateWriter implements AspectWriter {

  private final static String TEMPLATE_JAVA = "java_info.template.bzl";
  private final static String REALIZED_JAVA = "java_info.bzl";
  private final static String TEMPLATE_PYTHON = "python_info.template.bzl";
  private final static String REALIZED_PYTHON = "python_info.bzl";
  private final static String TEMPLATE_CODE_GENERATOR = "code_generator_info.template.bzl";
  private final static String REALIZED_CODE_GENERATOR = "code_generator_info.bzl";

  @Override
  public @NotNull String name() {
    return "Aspect Templates";
  }

  @Override
  public void write(@NotNull Path dst, @NotNull Project project) throws SyncFailedException {
    var manager = BlazeProjectDataManager.getInstance(project);

    if (manager == null) {
      throw new SyncFailedException("Couldn't get BlazeProjectDataManager");
    }

    try {
      writeLanguageInfos(manager, dst, project);
      writeCodeGeneratorInfo(manager, project, dst);
    } catch (IOException e) {
      throw new SyncFailedException("Failed to evaluate a template", e);
    }
  }

  private void writeCodeGeneratorInfo(
      BlazeProjectDataManager manager,
      Project project,
      Path dst
  ) throws IOException {
    final var viewSet = ProjectViewManager.getInstance(project).getProjectViewSet();
    final var languageClasses = Optional.ofNullable(manager.getBlazeProjectData())
        .map(BlazeProjectData::getWorkspaceLanguageSettings)
        .map(WorkspaceLanguageSettings::getActiveLanguages)
        .orElse(ImmutableSet.of());

    final var languageClassRuleNames = languageClasses.stream()
        .sorted()
        .map(lc -> new LanguageClassRuleNames(lc, ruleNamesForLanguageClass(lc, viewSet)))
        .toList();

    TemplateWriter.evaluate(
        dst,
        REALIZED_CODE_GENERATOR,
        ASPECT_TEMPLATE_DIRECTORY,
        TEMPLATE_CODE_GENERATOR,
        ImmutableMap.of("languageClassRuleNames", languageClassRuleNames)
    );
  }

  private void writeLanguageInfos(
      BlazeProjectDataManager manager,
      Path dst,
      Project project
  ) throws IOException {
    final var templateLanguageStringMap = getLanguageStringMap(manager, project);

    TemplateWriter.evaluate(
        dst,
        REALIZED_JAVA,
        ASPECT_TEMPLATE_DIRECTORY,
        TEMPLATE_JAVA,
        templateLanguageStringMap
    );
    TemplateWriter.evaluate(
        dst,
        REALIZED_PYTHON,
        ASPECT_TEMPLATE_DIRECTORY,
        TEMPLATE_PYTHON,
        templateLanguageStringMap
    );
  }

  private static @NotNull Map<String, String> getLanguageStringMap(BlazeProjectDataManager manager, Project project) {
    var projectData = Optional.ofNullable(manager.getBlazeProjectData()); // It can be empty on intial sync. Fall back to no language support
    var activeLanguages = projectData.map(it -> it.getWorkspaceLanguageSettings().getActiveLanguages()).orElse(ImmutableSet.of());
    var externalWorkspaceData = ExternalWorkspaceDataManager.getInstance(project).getData();
    var isAtLeastBazel8 = projectData.map(it -> it.getBlazeVersionData().bazelIsAtLeastVersion(8, 0, 0)).orElse(false);
    var isJavaEnabled = activeLanguages.contains(LanguageClass.JAVA) &&
            ((externalWorkspaceData != null && (!isAtLeastBazel8 || externalWorkspaceData.getByRepoName("rules_java") != null)));
    var isPythonEnabled = activeLanguages.contains(LanguageClass.PYTHON) &&
            ((externalWorkspaceData != null && (!isAtLeastBazel8 || externalWorkspaceData.getByRepoName("rules_python") != null)));
    return Map.of(
            "bazel8OrAbove", isAtLeastBazel8 ? "true" : "false",
            "isJavaEnabled", isJavaEnabled ? "true" : "false",
            "isPythonEnabled", isPythonEnabled ? "true" : "false"
    );
  }

  private static List<String> ruleNamesForLanguageClass(LanguageClass languageClass, ProjectViewSet viewSet) {
    Collection<String> ruleNames = CodeGeneratorRuleNameHelper.deriveRuleNames(viewSet, languageClass);

    // Do a check here to make sure that no invalid rule names have entered the system. This should
    // have been checked at the point of supply (see PythonCodeGeneratorRuleNamesSectionParser) but
    // to be sure in case the code flows change later.

    for (String ruleName : ruleNames) {
      if (!CodeGeneratorRuleNameHelper.isValidRuleName(ruleName)) {
        throw new IllegalStateException("the rule name [" + ruleName + "] is invalid");
      }
    }

    return ruleNames.stream().sorted().collect(Collectors.toUnmodifiableList());
  }

  /**
   * This class models a language class to its code-generator rule names.
   */
  public final static class LanguageClassRuleNames {
    private final LanguageClass languageClass;
    private final List<String> ruleNames;

    public LanguageClassRuleNames(LanguageClass languageClass, List<String> ruleNames) {
      this.languageClass = languageClass;
      this.ruleNames = ruleNames;
    }

    public LanguageClass getLanguageClass() {
      return languageClass;
    }

    @Override
    public String toString() {
      return String.format("[%s] -> [%s]", languageClass, String.join(",", ruleNames));
    }
  }
}
