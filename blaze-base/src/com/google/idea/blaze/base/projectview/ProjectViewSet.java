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
package com.google.idea.blaze.base.projectview;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.idea.blaze.base.projectview.section.ListSection;
import com.google.idea.blaze.base.projectview.section.ScalarSection;
import com.google.idea.blaze.base.projectview.section.Section;
import com.google.idea.blaze.base.projectview.section.SectionKey;

import javax.annotation.Nullable;
import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;

/**
 * A collection of project views and their file names.
 */
public final class ProjectViewSet implements Serializable {
  private static final long serialVersionUID = 1L;

  private final ImmutableList<ProjectViewFile> projectViewFiles;

  public ProjectViewSet(ImmutableList<ProjectViewFile> projectViewFiles) {
    this.projectViewFiles = projectViewFiles;
  }

  public <T> List<T> listItems(SectionKey<T, ListSection<T>> key) {
    List<T> result = Lists.newArrayList();
    for (ListSection<T> section : getSections(key)) {
      result.addAll(section.items());
    }
    return result;
  }

  @Nullable
  public <T> T getSectionValue(SectionKey<T, ScalarSection<T>> key) {
    return getSectionValue(key, null);
  }

  public <T> T getSectionValue(SectionKey<T, ScalarSection<T>> key, T defaultValue) {
    Collection<ScalarSection<T>> sections = getSections(key);
    if (sections.isEmpty()) {
      return defaultValue;
    } else {
      return Iterables.getLast(sections).getValue();
    }
  }

  public <T, SectionType extends Section<T>> Collection<SectionType> getSections(SectionKey<T, SectionType> key) {
    List<SectionType> result = Lists.newArrayList();
    for (ProjectViewFile projectViewFile : projectViewFiles) {
      ProjectView projectView = projectViewFile.projectView;
      SectionType section = projectView.getSectionOfType(key);
      if (section != null) {
        result.add(section);
      }
    }
    return result;
  }

  public Collection<ProjectViewFile> getProjectViewFiles() {
    return projectViewFiles;
  }

  @Nullable
  public ProjectViewFile getTopLevelProjectViewFile() {
    return !projectViewFiles.isEmpty() ? projectViewFiles.get(projectViewFiles.size() - 1) : null;
  }

  /**
   * A project view/file pair
   */
  public static class ProjectViewFile implements Serializable {
    private static final long serialVersionUID = 1L;
    public final ProjectView projectView;
    @Nullable public final File projectViewFile;

    public ProjectViewFile(ProjectView projectView, @Nullable File projectViewFile) {
      this.projectView = projectView;
      this.projectViewFile = projectViewFile;
    }
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    ImmutableList.Builder<ProjectViewFile> projectViewFiles = ImmutableList.builder();

    public Builder add(ProjectView projectView) {
      return add(null, projectView);
    }

    public Builder add(@Nullable File projectViewFile, ProjectView projectView) {
      projectViewFiles.add(new ProjectViewFile(projectView, projectViewFile));
      return this;
    }

    public Builder addAll(Collection<ProjectViewFile> projectViewFiles) {
      this.projectViewFiles.addAll(projectViewFiles);
      return this;
    }

    public ProjectViewSet build() {
      return new ProjectViewSet(projectViewFiles.build());
    }
  }
}
