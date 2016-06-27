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
package com.google.idea.blaze.base.projectview.section;

import com.google.common.collect.ImmutableList;

import javax.annotation.Nullable;
import java.util.Collection;

/**
 * List value. Eg.
 *
 * my_attribute:
 *  value0
 *  value1
 *  value2
 *  ...
 */
public final class ListSection<T> extends Section<T> {
  private static final long serialVersionUID = 1L;

  private final ImmutableList<T> items;

  ListSection(ImmutableList<T> items) {
    this.items = items;
  }

  public Collection<T> items() {
    return items;
  }

  public static <T> Builder<T> builder(SectionKey<T, ListSection<T>> sectionKey) {
    return new Builder<T>(sectionKey, null);
  }

  public static <T> Builder<T> update(SectionKey<T, ListSection<T>> sectionKey, @Nullable ListSection<T> section) {
    return new Builder<T>(sectionKey, section);
  }

  public static class Builder<T> extends SectionBuilder<T, ListSection<T>> {
    private final ImmutableList.Builder<T> items = ImmutableList.builder();

    public Builder(SectionKey<T, ListSection<T>> sectionKey, @Nullable ListSection<T> section) {
      super(sectionKey);
      if (section != null) {
        items.addAll(section.items);
      }
    }

    public final Builder<T> add(T item) {
      items.add(item);
      return this;
    }

    @Override
    public final ListSection<T> build() {
      return new ListSection<T>(items.build());
    }
  }
}
