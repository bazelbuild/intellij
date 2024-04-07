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
import com.google.common.collect.Lists;
import com.google.idea.blaze.base.projectview.parser.ParseContext;
import com.google.idea.blaze.base.projectview.parser.ProjectViewParser;
import com.google.idea.blaze.base.projectview.section.sections.ItemOrTextBlock;
import com.google.idea.blaze.base.projectview.section.sections.TextBlock;
import com.google.idea.blaze.base.projectview.section.sections.TextBlockSection;
import java.util.List;
import javax.annotation.Nullable;

/** List section parser base class. */
public abstract class ListSectionParser<T> extends SectionParser {
  private final SectionKey<T, ListSection<T>> key;

  protected ListSectionParser(SectionKey<T, ListSection<T>> key) {
    this.key = key;
  }

  @Override
  public SectionKey<T, ListSection<T>> getSectionKey() {
    return key;
  }

  @Nullable
  @Override
  public final ListSection<T> parse(ProjectViewParser parser, ParseContext parseContext) {
    if (parseContext.atEnd()) {
      return null;
    }

    String name = getName();
    if (!parseContext.current().text.equals(name + ':')) {
      return null;
    }
    int firstLineIndex = parseContext.getCurrentLineIndex();
    parseContext.consume();

    ImmutableList.Builder<ItemOrTextBlock<T>> builder = ImmutableList.builder();

    boolean correctIndentationRun = true;
    List<ItemOrTextBlock<T>> savedTextBlocks = Lists.newArrayList();
    while (!parseContext.atEnd()) {
      boolean isIndented = parseContext.current().indent == SectionParser.INDENT;
      if (!isIndented && correctIndentationRun) {
        parseContext.savePosition();
      }
      correctIndentationRun = isIndented;

      int currentLineIndex = parseContext.getCurrentLineIndex(); // save the current line number just in case multiple lines are consumed
      ItemOrTextBlock<T> itemOrTextBlock = null;
      TextBlock textBlock = TextBlockSection.parseTextBlock(parseContext);

      if (textBlock != null) {
        // the line has already been consumed so we need the address of the previous line
        itemOrTextBlock = new ItemOrTextBlock<>(textBlock, currentLineIndex);
      } else if (isIndented) {
        T item = parseItem(parser, parseContext);
        if (item != null) {
          itemOrTextBlock = new ItemOrTextBlock<>(item, parseContext.getCurrentLineIndex());
          parseContext.consume();
        }
      }

      if (itemOrTextBlock == null) {
        break;
      }

      if (isIndented) {
        builder.addAll(savedTextBlocks);
        builder.add(itemOrTextBlock);
        savedTextBlocks.clear();
        parseContext.clearSavedPosition();
      } else {
        savedTextBlocks.add(new ItemOrTextBlock<>(textBlock, parseContext.getCurrentLineIndex() - 1)); // The line is already consumed
      }
    }
    parseContext.resetToSavedPosition();

    ImmutableList<ItemOrTextBlock<T>> items = builder.build();
    if (items.isEmpty()) {
      parseContext.addError(String.format("Empty section: '%s'", name));
    }

    return new ListSection<>(key, items, firstLineIndex);
  }

  @SuppressWarnings("unchecked")
  @Override
  public final int print(StringBuilder sb, Section<?> section, int firstLineIndex) {
    ListSection<T> listSection = (ListSection<T>) section;
    int addedLinesNumber = 0;

    // Omit empty sections completely
    if (listSection.itemsOrComments().isEmpty()) {
      return addedLinesNumber;
    }

    sb.append(getName()).append(':').append('\n');
    addedLinesNumber += 1;
    for (ItemOrTextBlock<T> item : listSection.itemsOrComments()) {
      item.setLineIndex(firstLineIndex); // Fix line indexes since the caller does not know them

      if (item.item != null) {
        sb.append(" ".repeat(SectionParser.INDENT));
        printItem(item.item, sb);
        sb.append('\n');
        addedLinesNumber += 1;
      } else if (item.textBlock != null) {
        addedLinesNumber += item.textBlock.print(sb);
      }
    }

    return addedLinesNumber;
  }

  @Nullable
  protected abstract T parseItem(ProjectViewParser parser, ParseContext parseContext);

  protected abstract void printItem(T item, StringBuilder sb);
}
