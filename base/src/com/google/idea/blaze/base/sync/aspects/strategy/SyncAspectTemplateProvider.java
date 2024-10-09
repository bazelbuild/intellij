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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

class SyncAspectTemplateProvider implements SyncListener {
  private final Map<LanguageClass, String> supportedLanguageAspectTemplate = Map.of(
      LanguageClass.JAVA, "java_info.template.bzl",
      LanguageClass.GENERIC, "java_info.template.bzl"
  );

  @Override
  public void onSyncStart(Project project, BlazeContext context, SyncMode syncMode) throws SyncFailedException {
    copyProjectAspects(project);
    prepareProjectAspect(project);
  }

  private void copyProjectAspects(Project project) throws SyncFailedException {
    final var projectAspects = AspectRepositoryProvider.getProjectAspectDirectory(project).orElse(null);
    if (projectAspects == null) {
      throw new SyncFailedException("Missing project aspect directory");
    }

    // only copy project aspect once, TODO: do we need versioning here?
    if (projectAspects.exists()) return;

    final var templateAspects = AspectRepositoryProvider.findAspectTemplateDirectory().orElse(null);
    if (templateAspects == null || !templateAspects.isDirectory()) {
      throw new SyncFailedException("Missing aspect template directory");
    }

    try {
      copyFileTree(templateAspects.toPath(), projectAspects.toPath());
    } catch (IOException e) {
      throw new SyncFailedException("Could not copy aspect templates", e);
    }
  }

  private void copyFileTree(Path source, Path destination) throws IOException {
    try (final var fileStream = Files.walk(source)) {
      final var fileIterator = fileStream.iterator();
      while (fileIterator.hasNext()) {
        copyUsingRelativePath(source, fileIterator.next(), destination);
      }
    }
  }

  private void copyUsingRelativePath(Path sourcePrefix, Path source, Path destination) throws IOException {
    // only interested in bzl files that are templates
    if (source.endsWith(".bzl") && !source.endsWith("template.bzl")) return;

    final var sourceRelativePath = sourcePrefix.relativize(source).toString();
    final var destinationAbsolutePath = Paths.get(destination.toString(), sourceRelativePath);
    Files.copy(source, destinationAbsolutePath);
  }

  private void prepareProjectAspect(Project project) throws SyncFailedException {
    var manager = BlazeProjectDataManager.getInstance(project);
    if (manager == null) return;

    var projectData = manager.getBlazeProjectData();
    if (projectData == null) return;
    var optionalAspectTemplateDir = AspectRepositoryProvider.getProjectAspectDirectory(project);
    if (optionalAspectTemplateDir.isEmpty()) return;
    var aspectTemplateDir = optionalAspectTemplateDir.get().toPath();
    var templateWriter = new TemplateWriter(aspectTemplateDir);
    var activeLanguages = projectData.getWorkspaceLanguageSettings().getActiveLanguages();
    var isAtLeastBazel8 = projectData.getBlazeVersionData().bazelIsAtLeastVersion(8, 0, 0);
    var templateVariableMap = Map.of(
        "bazel8OrAbove", isAtLeastBazel8 ? "true" : "false",
        "isJavaEnabled", activeLanguages.contains(LanguageClass.JAVA) || activeLanguages.contains(LanguageClass.GENERIC) ? "true" : "false"
    );

    for (final var language : activeLanguages) {
      var templateFileName = supportedLanguageAspectTemplate.get(language);
      if (templateFileName == null) continue;

      var realizedFileName = templateFileName.replace(".template.bzl", ".bzl");
      var realizedFile = aspectTemplateDir.resolve(realizedFileName);

      if (!templateWriter.writeToFile(templateFileName, realizedFile, templateVariableMap)) {
        throw new SyncFailedException("Could not create template for: " + language);
      }
    }
  }
}