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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.idea.blaze.base.projectview.section.Section;
import com.google.idea.blaze.base.projectview.section.SectionBuilder;
import com.google.idea.blaze.base.projectview.section.SectionKey;
import com.intellij.openapi.diagnostic.Logger;

import javax.annotation.Nullable;
import java.io.Serializable;
import java.util.Map;

/**
 * Represents instructions for what should be included in a project.
 */
public final class ProjectView implements Serializable {
  private static final long serialVersionUID = 2L;

  private static final Logger LOG = Logger.getInstance(ProjectView.class);
  private final ImmutableMap<SectionKey, Section> sections;

  private ProjectView(ImmutableMap<SectionKey, Section> sections) {
    this.sections = sections;
  }

  @SuppressWarnings("unchecked")
  @Nullable
  public <T, SectionType extends Section<T>> SectionType getSectionOfType(SectionKey<T, SectionType> key) {
    return (SectionType) sections.get(key);
  }

  public static Builder builder() {
    return new Builder();
  }

  public static Builder builder(ProjectView projectView) {
    return new Builder(projectView);
  }

  /**
   * Builder class.
   */
  public static class Builder {
    private final Map<SectionKey, Section> sections;

    Builder() {
      sections = Maps.newHashMap();
    }

    Builder(ProjectView projectView) {
      sections = Maps.newHashMap(projectView.sections);
    }

    public <T, SectionType extends Section<T>> Builder put(SectionBuilder<T, SectionType> builder) {
      sections.put(builder.getSectionKey(), builder.build());
      return this;
    }

    public <T, SectionType extends Section<T>> Builder put(SectionKey<T, SectionType> key, SectionType section) {
      sections.put(key, section);
      return this;
    }

    @SuppressWarnings("unchecked")
    @Nullable
    public <T, SectionType extends Section<T>> SectionType get(SectionKey<T, SectionType> key) {
      return (SectionType) sections.get(key);
    }

    public ProjectView build() {
      return new ProjectView(ImmutableMap.copyOf(sections));
    }
  }
}
