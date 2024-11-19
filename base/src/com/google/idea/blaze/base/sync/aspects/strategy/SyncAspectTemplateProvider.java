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
package com.google.idea.blaze.base.sync.aspects.strategy;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.sync.SyncListener;
import com.google.idea.blaze.base.sync.SyncMode;
import com.google.idea.blaze.base.sync.SyncScope.SyncFailedException;
import com.google.idea.blaze.base.sync.codegenerator.CodeGeneratorRuleNameHelper;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.base.util.TemplateWriter;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;

public class SyncAspectTemplateProvider implements SyncListener {

  private final static String TEMPLATE_JAVA = "java_info.template.bzl";
  private final static String REALIZED_JAVA = "java_info.bzl";
  private final static String TEMPLATE_CODE_GENERATOR = "code_generator_info.template.bzl";
  private final static String REALIZED_CODE_GENERATOR = "code_generator_info.bzl";

  @Override
  public void onSyncStart(Project project, BlazeContext context, SyncMode syncMode) throws SyncFailedException {
    prepareProjectAspect(project);
  }

  private void prepareProjectAspect(Project project) throws SyncFailedException {
    var manager = BlazeProjectDataManager.getInstance(project);

    if (manager == null) {
      return;
    }

    var realizedAspectsPath = AspectRepositoryProvider
            .getProjectAspectDirectory(project)
            .map(File::toPath)
            .orElseThrow(() -> new SyncFailedException("Couldn't find project aspect directory"));

    try {
      Files.createDirectories(realizedAspectsPath);
      Files.writeString(realizedAspectsPath.resolve("WORKSPACE"), "");
      Files.writeString(realizedAspectsPath.resolve("BUILD"), "");
    } catch (IOException e) {
      throw new SyncFailedException("Couldn't create realized aspects", e);
    }

    final var templateAspects = AspectRepositoryProvider.findAspectTemplateDirectory()
            .orElseThrow(() -> new SyncFailedException("Couldn't find aspect template directory"));

    writeJavaInfo(manager, realizedAspectsPath, templateAspects, project);
    writeCodeGeneratorInfo(manager, project, realizedAspectsPath, templateAspects);
  }

  private void writeCodeGeneratorInfo(
      BlazeProjectDataManager manager,
      Project project,
      Path realizedAspectsPath,
      File templateAspects) throws SyncFailedException {
    ProjectViewSet viewSet = ProjectViewManager.getInstance(project).getProjectViewSet();
    Set<LanguageClass> languageClasses = Optional.ofNullable(manager.getBlazeProjectData())
        .map(BlazeProjectData::getWorkspaceLanguageSettings)
        .map(WorkspaceLanguageSettings::getActiveLanguages)
        .orElse(ImmutableSet.of());

    List<LanguageClassRuleNames> languageClassRuleNames = languageClasses.stream()
        .sorted()
        .map(lc -> new LanguageClassRuleNames(lc, ruleNamesForLanguageClass(lc, viewSet)))
        .collect(Collectors.toUnmodifiableList());

    var realizedFile = realizedAspectsPath.resolve(REALIZED_CODE_GENERATOR);
    var templateWriter = new TemplateWriter(templateAspects.toPath());
    var templateVariableMap = ImmutableMap.of("languageClassRuleNames", languageClassRuleNames);
    if (!templateWriter.writeToFile(TEMPLATE_CODE_GENERATOR, realizedFile, templateVariableMap)) {
      throw new SyncFailedException("Could not create template for: " + REALIZED_CODE_GENERATOR);
    }
  }

  private void writeJavaInfo(
          BlazeProjectDataManager manager,
          Path realizedAspectsPath,
          File templateAspects, Project project) throws SyncFailedException {
    var realizedFile = realizedAspectsPath.resolve(REALIZED_JAVA);
    var templateWriter = new TemplateWriter(templateAspects.toPath());
    var templateVariableMap = getJavaStringStringMap(manager, project);
    if (!templateWriter.writeToFile(TEMPLATE_JAVA, realizedFile, templateVariableMap)) {
      throw new SyncFailedException("Could not create template for: " + REALIZED_JAVA);
    }
  }

  private static @NotNull Map<String, String> getJavaStringStringMap(BlazeProjectDataManager manager, Project project) throws SyncFailedException {
    var projectData = Optional.ofNullable(manager.getBlazeProjectData()); // It can be empty on intial sync. Fall back to no lauguage support
    var blazeProjectData = BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    var activeLanguages = projectData.map(it -> it.getWorkspaceLanguageSettings().getActiveLanguages()).orElse(ImmutableSet.of());
    var isJavaEnabled = activeLanguages.contains(LanguageClass.JAVA)
            && blazeProjectData != null
            && blazeProjectData.getExternalWorkspaceData().getByRepoName("rules_java") != null;
    var isAtLeastBazel8 = projectData.map(it -> it.getBlazeVersionData().bazelIsAtLeastVersion(8, 0, 0)).orElse(false);
    return Map.of(
            "bazel8OrAbove", isAtLeastBazel8 ? "true" : "false",
            "isJavaEnabled", isJavaEnabled ? "true" : "false"
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

    public List<String> getRuleNames() {
      return ruleNames;
    }

    @Override
    public String toString() {
      return String.format("[%s] -> [%s]", languageClass, String.join(",", ruleNames));
    }

  }

}