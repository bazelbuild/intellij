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
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.sync.workspace.WorkspaceHelper;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import io.protostuff.jetbrains.plugin.settings.ProtobufSettings;

import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class ProtoSettingsSyncUpdater {
  static void syncSettings(Project project, ProjectViewSet projectViewSet) {
    List<String> toInclude =
        Stream.concat(
                getStaticImportRoots(project),
                ProtoSettingsSyncSections.getProtoImportRoots(project, projectViewSet))
            .distinct()
            .collect(Collectors.toList());

    if (!toInclude.isEmpty()) {
      ApplicationManager.getApplication()
          .invokeLater(
              () -> {
                ProtobufSettings protobufSettings = ProtobufSettings.getInstance(project);
                HashSet<String> pathsToUpdate = new HashSet<>(protobufSettings.getIncludePaths());
                pathsToUpdate.addAll(toInclude);
                if (pathsToUpdate.size() > protobufSettings.getIncludePaths().size()) {
                  protobufSettings.setIncludePaths(Lists.newArrayList(pathsToUpdate));
                }
              });
    }
  }

  private static Stream<String> getStaticImportRoots(Project project) {
    WorkspaceRoot protobufWorkspaceRoot =
        WorkspaceHelper.resolveExternalWorkspace(project, "com_google_protobuf");
    if (protobufWorkspaceRoot != null) {
      // the Protobuf WKTs are discoverable from here, add these by default..
      return Stream.of(protobufWorkspaceRoot.directory().toPath().resolve("src").toString());
    } else {
      return Stream.empty();
    }
  }
}
