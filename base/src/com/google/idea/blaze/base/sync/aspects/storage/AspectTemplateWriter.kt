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
package com.google.idea.blaze.base.sync.aspects.storage

import com.google.common.collect.ImmutableMap
import com.google.idea.blaze.base.model.primitives.LanguageClass
import com.google.idea.blaze.base.projectview.ProjectViewManager
import com.google.idea.blaze.base.projectview.ProjectViewSet
import com.google.idea.blaze.base.sync.SyncProjectState
import com.google.idea.blaze.base.sync.SyncScope.SyncFailedException
import com.google.idea.blaze.base.sync.aspects.storage.AspectRepositoryProvider.ASPECT_TEMPLATE_DIRECTORY
import com.google.idea.blaze.base.sync.codegenerator.CodeGeneratorRuleNameHelper
import com.google.idea.blaze.base.util.TemplateWriter
import com.intellij.openapi.project.Project
import java.io.IOException
import java.nio.file.Path

private const val TEMPLATE_JAVA = "java_info.template.bzl"
private const val REALIZED_JAVA = "java_info.bzl"
private const val TEMPLATE_PYTHON = "python_info.template.bzl"
private const val REALIZED_PYTHON = "python_info.bzl"
private const val TEMPLATE_SCALA = "scala_info.template.bzl"
private const val REALIZED_SCALA = "scala_info.bzl"
private const val TEMPLATE_CODE_GENERATOR = "code_generator_info.template.bzl"
private const val REALIZED_CODE_GENERATOR = "code_generator_info.bzl"
private const val TEMPLATE_INTELLIJ_INFO = "intellij_info.template.bzl"
private const val REALIZED_INTELLIJ_INFO = "intellij_info_bundled.bzl"

class AspectTemplateWriter : AspectWriter {

  override fun name(): String = "Aspect Templates"

  @Throws(SyncFailedException::class)
  override fun write(dst: Path, project: Project, state: SyncProjectState) {
    try {
      writeLanguageInfos(state, dst)
      writeCodeGeneratorInfo(state, project, dst)
    } catch (e: IOException) {
      throw SyncFailedException("Failed to evaluate a template", e)
    }
  }

  @Throws(IOException::class)
  private fun writeCodeGeneratorInfo(state: SyncProjectState, project: Project, dst: Path) {
    val viewSet = ProjectViewManager.getInstance(project).projectViewSet
    val languageClasses = state.languageSettings.activeLanguages

    val languageClassRuleNames = languageClasses.stream()
      .sorted()
      .map { LanguageClassRuleNames(it, ruleNamesForLanguageClass(it, viewSet)) }
      .toList()

    TemplateWriter.evaluate(
      dst,
      REALIZED_CODE_GENERATOR,
      ASPECT_TEMPLATE_DIRECTORY,
      TEMPLATE_CODE_GENERATOR,
      ImmutableMap.of<String, List<LanguageClassRuleNames>>("languageClassRuleNames", languageClassRuleNames)
    )
  }

  @Throws(IOException::class)
  private fun writeLanguageInfos(state: SyncProjectState, dst: Path) {
    val templateOptions = getTemplateOptions(state)

    TemplateWriter.evaluate(
      dst,
      REALIZED_JAVA,
      ASPECT_TEMPLATE_DIRECTORY,
      TEMPLATE_JAVA,
      templateOptions
    )
    TemplateWriter.evaluate(
      dst,
      REALIZED_PYTHON,
      ASPECT_TEMPLATE_DIRECTORY,
      TEMPLATE_PYTHON,
      templateOptions
    )
    TemplateWriter.evaluate(
      dst,
      REALIZED_SCALA,
      ASPECT_TEMPLATE_DIRECTORY,
      TEMPLATE_SCALA,
      templateOptions
    )
    TemplateWriter.evaluate(
      dst,
      REALIZED_INTELLIJ_INFO,
      ASPECT_TEMPLATE_DIRECTORY,
      TEMPLATE_INTELLIJ_INFO,
      templateOptions
    )
  }

  private fun getTemplateOptions(state: SyncProjectState): ImmutableMap<String, String> {
    val activeLanguages = state.languageSettings.activeLanguages
    val externalWorkspaceData = state.externalWorkspaceData
    val isAtLeastBazel8 = state.blazeVersionData.bazelIsAtLeastVersion(8, 0, 0)
    val isAtLeastBazel9 = state.blazeVersionData.bazelIsAtLeastVersion(9, 0, 0)
    val isNotBzlmod = state.blazeInfo.starlarkSemantics?.contains("enable_bzlmod=false") ?: false
    fun hasRepository(name: String) = externalWorkspaceData?.getByRepoName(name) != null

    val isJavaEnabled = activeLanguages.contains(LanguageClass.JAVA) &&
        (externalWorkspaceData != null && (!isAtLeastBazel8 || isNotBzlmod || hasRepository("rules_java")))

    val isPythonEnabled = activeLanguages.contains(LanguageClass.PYTHON) &&
        (externalWorkspaceData != null && (!isAtLeastBazel8 || isNotBzlmod || hasRepository("rules_python")))

    val isScalaEnabled = activeLanguages.contains(LanguageClass.SCALA) &&
        (externalWorkspaceData != null && (!isAtLeastBazel8 || isNotBzlmod || hasRepository("rules_scala")))

    return ImmutableMap.of(
      "bazel8OrAbove", if (isAtLeastBazel8) "true" else "false",
      "bazel9OrAbove", if (isAtLeastBazel9) "true" else "false",
      "isJavaEnabled", if (isJavaEnabled) "true" else "false",
      "isPythonEnabled", if (isPythonEnabled) "true" else "false",
      "isScalaEnabled", if (isScalaEnabled) "true" else "false"
    )
  }

  /** This class models a language class to its code-generator rule names. */
  class LanguageClassRuleNames(val languageClass: LanguageClass, val ruleNames: List<String>) {

    override fun toString(): String {
      return String.format("[%s] -> [%s]", languageClass, ruleNames.joinToString(","))
    }
  }

  private fun ruleNamesForLanguageClass(languageClass: LanguageClass, viewSet: ProjectViewSet?): List<String> {
    val ruleNames: Collection<String> = CodeGeneratorRuleNameHelper.deriveRuleNames(viewSet, languageClass)

    // Do a check here to make sure that no invalid rule names have entered the system. This should
    // have been checked at the point of supply (see PythonCodeGeneratorRuleNamesSectionParser) but
    // to be sure in case the code flows change later.
    for (ruleName in ruleNames) {
      check(CodeGeneratorRuleNameHelper.isValidRuleName(ruleName)) { "the rule name [$ruleName] is invalid" }
    }

    return ruleNames.stream().sorted().toList()
  }
}
