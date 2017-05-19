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

import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspaceType;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.section.sections.AdditionalLanguagesSection;
import com.google.idea.blaze.base.projectview.section.sections.WorkspaceTypeSection;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.sync.BlazeSyncPlugin;
import com.intellij.openapi.diagnostic.Logger;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import javax.annotation.Nullable;

/** Reads the user's language preferences from the project view. */
public class LanguageSupport {

  private static final Logger logger = Logger.getInstance(LanguageSupport.class);

  @Nullable
  public static WorkspaceType getDefaultWorkspaceType() {
    WorkspaceType workspaceType = null;
    // prioritize by enum ordinal.
    for (BlazeSyncPlugin syncPlugin : BlazeSyncPlugin.EP_NAME.getExtensions()) {
      WorkspaceType recommendedType = syncPlugin.getDefaultWorkspaceType();
      if (recommendedType != null
          && (workspaceType == null || workspaceType.ordinal() > recommendedType.ordinal())) {
        workspaceType = recommendedType;
      }
    }
    return workspaceType;
  }

  /**
   * Derives {@link WorkspaceLanguageSettings} from the {@link ProjectViewSet}. Does no validation.
   */
  @Nullable
  public static WorkspaceLanguageSettings createWorkspaceLanguageSettings(
      ProjectViewSet projectViewSet) {
    WorkspaceType workspaceType = projectViewSet.getScalarValue(WorkspaceTypeSection.KEY);
    if (workspaceType == null) {
      workspaceType = getDefaultWorkspaceType();
    }

    if (workspaceType == null) {
      logger.error("Could not find workspace type."); // Should never happen
      return null;
    }

    ImmutableSet.Builder<LanguageClass> activeLanguages =
        ImmutableSet.<LanguageClass>builder()
            .addAll(workspaceType.getLanguages())
            .addAll(projectViewSet.listItems(AdditionalLanguagesSection.KEY))
            .add(LanguageClass.GENERIC);
    Arrays.stream(BlazeSyncPlugin.EP_NAME.getExtensions())
        .forEach(plugin -> activeLanguages.addAll(plugin.getAlwaysActiveLanguages()));
    return new WorkspaceLanguageSettings(workspaceType, activeLanguages.build());
  }

  public static boolean validateLanguageSettings(
      BlazeContext context, WorkspaceLanguageSettings languageSettings) {
    Set<WorkspaceType> supportedTypes = supportedWorkspaceTypes();
    WorkspaceType workspaceType = languageSettings.getWorkspaceType();
    if (!supportedTypes.contains(languageSettings.getWorkspaceType())) {
      IssueOutput.error(
              String.format(
                  "Workspace type '%s' is not supported by this plugin",
                  languageSettings.getWorkspaceType().getName()))
          .submit(context);
      return false;
    }
    Set<LanguageClass> supportedLanguages = supportedLanguagesForWorkspaceType(workspaceType);
    Set<LanguageClass> availableLanguages = EnumSet.noneOf(LanguageClass.class);
    for (WorkspaceType type : supportedTypes) {
      availableLanguages.addAll(supportedLanguagesForWorkspaceType(type));
    }

    for (LanguageClass languageClass : languageSettings.activeLanguages) {
      if (!availableLanguages.contains(languageClass)) {
        IssueOutput.error(
                String.format(
                    "Language '%s' is not supported by this plugin", languageClass.getName()))
            .submit(context);
        return false;
      }
      if (!supportedLanguages.contains(languageClass)) {
        IssueOutput.error(
                String.format(
                    "Language '%s' is not supported for this plugin with workspace type: '%s'",
                    languageClass.getName(), workspaceType.getName()))
            .submit(context);
        return false;
      }
    }
    return true;
  }

  /** The {@link WorkspaceType}s supported by this plugin */
  private static Set<WorkspaceType> supportedWorkspaceTypes() {
    Set<WorkspaceType> supportedTypes = EnumSet.noneOf(WorkspaceType.class);
    for (BlazeSyncPlugin syncPlugin : BlazeSyncPlugin.EP_NAME.getExtensions()) {
      supportedTypes.addAll(syncPlugin.getSupportedWorkspaceTypes());
    }
    return supportedTypes;
  }

  /** @return The set of {@link LanguageClass}'s supported for this {@link WorkspaceType}s. */
  public static Set<LanguageClass> supportedLanguagesForWorkspaceType(WorkspaceType type) {
    Set<LanguageClass> supportedLanguages = EnumSet.noneOf(LanguageClass.class);
    for (BlazeSyncPlugin syncPlugin : BlazeSyncPlugin.EP_NAME.getExtensions()) {
      supportedLanguages.addAll(syncPlugin.getSupportedLanguagesInWorkspace(type));
      supportedLanguages.addAll(syncPlugin.getAlwaysActiveLanguages());
    }
    supportedLanguages.add(LanguageClass.GENERIC);
    return supportedLanguages;
  }

  /** @return The valid 'additional_language' options for this workspace type */
  public static Set<LanguageClass> availableAdditionalLanguages(WorkspaceType workspaceType) {
    Set<LanguageClass> langs = LanguageSupport.supportedLanguagesForWorkspaceType(workspaceType);
    langs.removeAll(workspaceType.getLanguages());
    langs.remove(LanguageClass.GENERIC);

    Arrays.stream(BlazeSyncPlugin.EP_NAME.getExtensions())
        .forEach(plugin -> langs.removeAll(plugin.getAlwaysActiveLanguages()));

    return langs;
  }
}
