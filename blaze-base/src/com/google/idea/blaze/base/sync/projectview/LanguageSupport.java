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
package com.google.idea.blaze.base.sync.projectview;

import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspaceType;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.section.ScalarSection;
import com.google.idea.blaze.base.projectview.section.sections.AdditionalLanguagesSection;
import com.google.idea.blaze.base.projectview.section.sections.WorkspaceTypeSection;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.sync.BlazeSyncPlugin;
import com.intellij.openapi.diagnostic.Logger;

import java.util.Collection;
import java.util.Set;

/**
 * Reads the user's language preferences from the project view.
 */
public class LanguageSupport {

  private static final Logger LOG = Logger.getInstance(LanguageSupport.class);

  public static WorkspaceLanguageSettings createWorkspaceLanguageSettings(BlazeContext context, ProjectViewSet projectViewSet) {
    WorkspaceType workspaceType = projectViewSet.getSectionValue(WorkspaceTypeSection.KEY);
    if (workspaceType == null) {
      for (BlazeSyncPlugin syncPlugin : BlazeSyncPlugin.EP_NAME.getExtensions()) {
        WorkspaceType pluginWorkspaceType = syncPlugin.getDefaultWorkspaceType();
        if (pluginWorkspaceType != null) {
          if (workspaceType == null || workspaceType.ordinal() < pluginWorkspaceType.ordinal()) {
            workspaceType = pluginWorkspaceType;
          }
        }
      }
    }

    if (workspaceType == null) {
      LOG.error("Could not find workspace type."); // Should never happen
      return null;
    }

    Set<LanguageClass> activeLanguages = Sets.newHashSet();
    for (LanguageClass languageClass : workspaceType.getLanguages()) {
      activeLanguages.add(languageClass);
    }
    activeLanguages.addAll(projectViewSet.listItems(AdditionalLanguagesSection.KEY));

    Set<LanguageClass> supportedLanguages = Sets.newHashSet();
    for (BlazeSyncPlugin syncPlugin : BlazeSyncPlugin.EP_NAME.getExtensions()) {
      supportedLanguages.addAll(syncPlugin.getSupportedLanguagesInWorkspace(workspaceType));
    }

    for (LanguageClass languageClass : activeLanguages) {
      if (!supportedLanguages.contains(languageClass)) {
        IssueOutput
          .error(String.format(
            "Language '%s' is not supported for this plugin with workspace type: '%s'",
            languageClass.getName(), workspaceType.getName()))
          .submit(context);
        return null;
      }
    }

    return new WorkspaceLanguageSettings(workspaceType, activeLanguages);
  }
}
