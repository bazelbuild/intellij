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

import java.util.regex.PatternSyntaxException;

/**
 * Parses glob sections.
 */
public class GlobSectionParser extends ListSectionParser<Glob> {

  public GlobSectionParser(SectionKey<Glob, ListSection<Glob>> key) {
    super(key);
  }

  @Override
  protected final void parseItem(ProjectViewParser parser,
                                 ParseContext parseContext,
                                 ImmutableList.Builder<Glob> items) {
    String text = parseContext.current().text;
    try {
      Glob glob = new Glob(text);
      items.add(glob);
    }
    catch (PatternSyntaxException e) {
      parseContext.addError(e.getMessage());
    }
  }

  @Override
  protected final void printItem(Glob item, StringBuilder sb) {
    sb.append(item.toString());
  }

  @Override
  public ItemType getItemType() {
    return ItemType.FileSystemItem;
  }

}
