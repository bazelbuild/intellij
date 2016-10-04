/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.projectview;

import com.google.idea.blaze.base.projectview.parser.ParseContext;
import com.google.idea.blaze.base.projectview.parser.ProjectViewParser;
import com.google.idea.blaze.base.projectview.section.ScalarSection;
import com.google.idea.blaze.base.projectview.section.ScalarSectionParser;
import com.google.idea.blaze.base.projectview.section.SectionKey;
import com.google.idea.blaze.base.projectview.section.SectionParser;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nullable;

/** Allows manual override of the android sdk. */
public class AndroidSdkPlatformSection {
  public static final SectionKey<String, ScalarSection<String>> KEY =
      SectionKey.of("android_sdk_platform");
  public static final SectionParser PARSER = new AndroidSdkPlatformParser();

  private static class AndroidSdkPlatformParser extends ScalarSectionParser<String> {
    public AndroidSdkPlatformParser() {
      super(KEY, ':');
    }

    @Nullable
    @Override
    protected String parseItem(ProjectViewParser parser, ParseContext parseContext, String rest) {
      return StringUtil.unquoteString(rest);
    }

    @Override
    protected void printItem(StringBuilder sb, String value) {
      sb.append(value);
    }

    @Override
    public ItemType getItemType() {
      return ItemType.Other;
    }
  }
}
