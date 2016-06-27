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
import com.google.idea.blaze.base.projectview.parser.ParseContext;
import com.google.idea.blaze.base.projectview.parser.ProjectViewParser;

import javax.annotation.Nullable;

/**
 * List section parser base class.
 */
public abstract class ListSectionParser<T> extends SectionParser {
  protected ListSectionParser(SectionKey<T, ? extends ListSection<T>> key) {
    super(key);
  }

  @Nullable
  @Override
  public final Section parse(ProjectViewParser parser, ParseContext parseContext) {
    if (parseContext.atEnd()) {
      return null;
    }

    String name = getName();

    if (!parseContext.current().text.equals(name + ':')) {
      return null;
    }
    parseContext.consume();

    ImmutableList.Builder<T> builder = ImmutableList.builder();

    while (!parseContext.atEnd() && parseContext.current().indent >= SectionParser.INDENT) {
      parseItem(parser, parseContext, builder);
      parseContext.consume();
    }

    ImmutableList<T> items = builder.build();
    if (items.isEmpty()) {
      parseContext.addError(String.format("Empty section: '%s'", name));
    }

    return new ListSection<T>(items);
  }

  @SuppressWarnings("unchecked")
  @Override
  public final void print(StringBuilder sb, Section section) {
    ListSection<T> listSection = (ListSection<T>)section;

    // Omit empty sections completely
    if (listSection.items().isEmpty()) {
      return;
    }

    sb.append(getName()).append(':').append('\n');
    for (T item : listSection.items()) {
      for (int i = 0; i < SectionParser.INDENT; ++i) {
        sb.append(' ');
      }
      printItem(item, sb);
      sb.append('\n');
    }
  }

  protected abstract void parseItem(ProjectViewParser parser,
                                    ParseContext parseContext,
                                    ImmutableList.Builder<T> items);

  protected abstract void printItem(T item, StringBuilder sb);
}
