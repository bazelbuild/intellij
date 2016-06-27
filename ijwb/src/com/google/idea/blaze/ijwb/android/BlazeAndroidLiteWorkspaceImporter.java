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
package com.google.idea.blaze.ijwb.android;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.base.ideinfo.AndroidRuleIdeInfo;
import com.google.idea.blaze.base.ideinfo.LibraryArtifact;
import com.google.idea.blaze.base.ideinfo.RuleIdeInfo;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.sync.projectview.ProjectViewRuleImportFilter;
import com.google.idea.blaze.java.sync.model.BlazeLibrary;
import com.google.idea.blaze.java.sync.model.LibraryKey;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Builds a BlazeWorkspace.
 */
public final class BlazeAndroidLiteWorkspaceImporter {
  private static final Logger LOG = Logger.getInstance(BlazeAndroidLiteWorkspaceImporter.class);

  private final Project project;
  private final BlazeContext context;
  private final ImmutableMap<Label, RuleIdeInfo> ruleMap;
  private final ProjectViewRuleImportFilter importFilter;

  public BlazeAndroidLiteWorkspaceImporter(
    Project project,
    WorkspaceRoot workspaceRoot,
    BlazeContext context,
    ProjectViewSet projectViewSet,
    ImmutableMap<Label, RuleIdeInfo> ruleMap) {
    this.project = project;
    this.context = context;
    this.ruleMap = ruleMap;
    this.importFilter = new ProjectViewRuleImportFilter(project, workspaceRoot, projectViewSet);
  }

  public BlazeAndroidLiteImportResult importWorkspace() {
    List<RuleIdeInfo> rules = ruleMap.values()
      .stream()
      .filter(importFilter::isSourceRule)
      .filter(rule -> rule.kind.getLanguageClass() == LanguageClass.ANDROID)
      .filter(rule -> !importFilter.excludeTarget(rule))
      .collect(Collectors.toList());

    WorkspaceBuilder workspaceBuilder = new WorkspaceBuilder();

    for (RuleIdeInfo rule : rules) {
      addRuleAsSource(
        workspaceBuilder,
        rule
      );
    }

    return new BlazeAndroidLiteImportResult(
      workspaceBuilder.libraries.build()
    );
  }

  private void addRuleAsSource(
    WorkspaceBuilder workspaceBuilder,
    RuleIdeInfo rule) {

    AndroidRuleIdeInfo androidRuleIdeInfo = rule.androidRuleIdeInfo;
    if (androidRuleIdeInfo != null) {
      // Add R.java jars
      LibraryArtifact resourceJar = androidRuleIdeInfo.resourceJar;
      if (resourceJar != null) {
        BlazeLibrary library1 = new BlazeLibrary(LibraryKey.fromJarFile(resourceJar.jar.getFile()), resourceJar);
        workspaceBuilder.libraries.add(library1);
      }

      LibraryArtifact idlJar = androidRuleIdeInfo.idlJar;
      if (idlJar != null) {
        BlazeLibrary library = new BlazeLibrary(LibraryKey.fromJarFile(idlJar.jar.getFile()), idlJar);
        workspaceBuilder.libraries.add(library);
      }
    }
  }

  static class WorkspaceBuilder {
    ImmutableList.Builder<BlazeLibrary> libraries = ImmutableList.builder();
  }
}
