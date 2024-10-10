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

import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.sync.SyncListener;
import com.google.idea.blaze.base.sync.SyncMode;
import com.google.idea.blaze.base.sync.SyncScope.SyncFailedException;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.util.TemplateWriter;
import com.intellij.openapi.project.Project;

import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

public class SyncAspectTemplateProvider implements SyncListener {
  private final Map<LanguageClass, String> supportedLanguageAspectTemplate = Map.of(
      LanguageClass.JAVA, "java_info.template.bzl",
      LanguageClass.GENERIC, "java_info.template.bzl"
  );

  @Override
  public void onSyncStart(Project project, BlazeContext context, SyncMode syncMode) throws SyncFailedException {
    prepareProjectAspect(project);
  }

  private void prepareProjectAspect(Project project) throws SyncFailedException {
    var manager = BlazeProjectDataManager.getInstance(project);
    if (manager == null) return;
    var projectData = manager.getBlazeProjectData();
    if (projectData == null) return;
    var realizedAspectsPath = AspectRepositoryProvider.getProjectAspectDirectory(project).get().toPath();
    try {
      Files.createDirectories(realizedAspectsPath);
      Files.writeString(realizedAspectsPath.resolve("WORKSPACE"), "");
      Files.writeString(realizedAspectsPath.resolve("BUILD"), "");
    } catch (IOException e) {
      throw new SyncFailedException("Couldn't create realized aspects", e);
    }


    final var templateAspects = AspectRepositoryProvider.findAspectTemplateDirectory().orElse(null);
    var javaTemplate = "java_info.template.bzl";
    var templateWriter = new TemplateWriter(templateAspects.toPath());
    var activeLanguages = projectData.getWorkspaceLanguageSettings().getActiveLanguages();
    var isAtLeastBazel8 = projectData.getBlazeVersionData().bazelIsAtLeastVersion(8, 0, 0);
    var templateVariableMap = Map.of(
            "bazel8OrAbove", isAtLeastBazel8 ? "true" : "false",
            "isJavaEnabled", activeLanguages.contains(LanguageClass.JAVA) || activeLanguages.contains(LanguageClass.GENERIC) ? "true" : "false"
    );
    var realizedFile = realizedAspectsPath.resolve("java_info.bzl");
    if (!templateWriter.writeToFile(javaTemplate, realizedFile, templateVariableMap)) {
      throw new SyncFailedException("Could not create template for: ");
    }
  }
}