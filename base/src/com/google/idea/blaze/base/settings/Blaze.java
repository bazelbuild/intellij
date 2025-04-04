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
package com.google.idea.blaze.base.settings;

import com.google.idea.blaze.base.bazel.BuildSystemProvider;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.section.sections.UseQuerySyncSection;
import com.google.idea.blaze.base.scope.Scope;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.settings.BlazeImportSettings.ProjectType;
import com.intellij.ide.DataManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.SwingUtilities;
import java.util.Optional;

/** Blaze project utilities. */
public class Blaze {

  private Blaze() {}

  /**
   * Returns whether this project was imported from blaze.
   *
   * @deprecated use {@link #getProjectType(Project)}.
   */
  @Deprecated
  public static boolean isBlazeProject(@Nullable Project project) {
    return project != null
        && BlazeImportSettingsManager.getInstance(project).getImportSettings() != null;
  }

  public static BlazeProjectData getProjectData(@Nullable Project project) {
    if (!isBlazeProject(project)) {
      return null;
    }

    return BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
  }

  /**
   * Returns the ProjectType of this imported project. {@code ProjectType.UNKNOWN} will be returned
   * if the project is not available, not imported from blaze, or we failed to access its import
   * settings.
   */
  public static ProjectType getProjectType(@Nullable Project project) {
    if (project == null) {
      return ProjectType.UNKNOWN;
    }

    BlazeImportSettingsManager blazeImportSettingsManager =
        BlazeImportSettingsManager.getInstance(project);
    if (blazeImportSettingsManager == null) {
      return ProjectType.UNKNOWN;
    }
    BlazeImportSettings blazeImportSettings = blazeImportSettingsManager.getImportSettings();
    if (blazeImportSettings == null) {
      return ProjectType.UNKNOWN;
    }
    return blazeImportSettings.getProjectType();
  }

  /**
   * This variant allows us to enable and disable Query Sync for already imported project.
   * com.google.idea.blaze.base.settings.Blaze#getProjectType(com.intellij.openapi.project.Project) is called quite often
   * so we cannot reload project view from for every of such call.
   * This is why we have this special case to make sure that Sync respects project view selection if there is any.
   */
  public static ProjectType getUpToDateProjectTypeBeforeSync(@Nonnull Project project) {
    BlazeImportSettingsManager blazeImportSettingsManager =
            BlazeImportSettingsManager.getInstance(project);
    if (blazeImportSettingsManager == null) {
      return ProjectType.UNKNOWN;
    }
    BlazeImportSettings blazeImportSettings = blazeImportSettingsManager.getImportSettings();
    if (blazeImportSettings == null) {
      return ProjectType.UNKNOWN;
    }
    ProjectViewSet projectViewSet = Scope.root(
            context -> {
              return ProjectViewManager.getInstance(project).reloadProjectView(context);
            });

    if (projectViewSet == null) {
      // fallback existing type if project view file is not valid
      return blazeImportSettings.getProjectType();
    }

    Optional<Boolean> querySyncProjectView = projectViewSet.getScalarValue(UseQuerySyncSection.KEY);
    if (querySyncProjectView.isPresent()) {
      if (blazeImportSettings.getProjectType() == ProjectType.QUERY_SYNC && !querySyncProjectView.get()) {
        blazeImportSettings.setProjectType(ProjectType.ASPECT_SYNC);
      } else if (blazeImportSettings.getProjectType() == ProjectType.ASPECT_SYNC && querySyncProjectView.get()) {
        blazeImportSettings.setProjectType(ProjectType.QUERY_SYNC);
      }
    }

    return getProjectType(project);
  }

  /**
   * Returns the build system associated with this project, or falls back to the default blaze build
   * system if the project is null or not a blaze project.
   */
  public static BuildSystemName getBuildSystemName(@Nullable Project project) {
    BlazeImportSettings importSettings =
        project == null
            ? null
            : BlazeImportSettingsManager.getInstance(project).getImportSettings();
    if (importSettings == null) {
      return BuildSystemProvider.defaultBuildSystem().buildSystem();
    }
    return importSettings.getBuildSystem();
  }

  /**
   * Returns the build system provider associated with this project, or falls back to the default
   * blaze build system if the project is null or not a blaze project.
   */
  public static BuildSystemProvider getBuildSystemProvider(@Nullable Project project) {
    BuildSystemProvider provider =
        BuildSystemProvider.getBuildSystemProvider(getBuildSystemName(project));
    return provider != null ? provider : BuildSystemProvider.defaultBuildSystem();
  }

  /**
   * The name of the build system associated with the given project, or falls back to the default
   * blaze build system if the project is null or not a blaze project.
   */
  public static String buildSystemName(@Nullable Project project) {
    return getBuildSystemName(project).getName();
  }

  /** The default build system */
  public static BuildSystemName defaultBuildSystem() {
    return BuildSystemProvider.defaultBuildSystem().buildSystem();
  }

  /**
   * The name of the application-wide build system default. This should only be used in situations
   * where it doesn't make sense to use the build system associated with the current project (e.g.
   * the import project action).
   */
  public static String defaultBuildSystemName() {
    return BuildSystemProvider.defaultBuildSystem().buildSystem().getName();
  }

  /**
   * Tries to guess the current project, and uses that to determine the build system name.<br>
   * Should only be used in situations where the current project is not accessible.
   */
  public static String guessBuildSystemName() {
    Project project = guessCurrentProject();
    return buildSystemName(project);
  }

  private static Project guessCurrentProject() {
    Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
    if (openProjects.length == 1) {
      return openProjects[0];
    }
    if (SwingUtilities.isEventDispatchThread()) {
      return (Project) DataManager.getInstance().getDataContext().getData("project");
    }
    return null;
  }
}
