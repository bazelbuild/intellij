/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.contrib.protosync;

import com.google.common.collect.Lists;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.StatusOutput;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import io.protostuff.jetbrains.plugin.settings.ProtobufSettings;

import java.util.List;
import java.util.stream.Collectors;

public final class ProtoSettingsSyncUpdater {
  static void syncSettings(Project project, ProjectViewSet projectViewSet, BlazeContext context) {
    ApplicationManager.getApplication()
        .invokeLater(
            () -> {
              ProtobufSettings protobufSettings = ProtobufSettings.getInstance(project);
              List<String> toInclude =
                  ProtoSettingsSyncSections.getProtoImportRoots(project, projectViewSet)
                      .distinct()
                      .collect(Collectors.toList());

              context.output(
                  new StatusOutput(
                      toInclude
                          .stream()
                          .collect(
                              Collectors.joining(
                                  "\n\t", "configuring proto import root directories: \n\t", ""))));
              protobufSettings.setIncludePaths(Lists.newArrayList(toInclude));
            });
  }
}
