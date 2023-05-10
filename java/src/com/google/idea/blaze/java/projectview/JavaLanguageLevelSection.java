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
package com.google.idea.blaze.java.projectview;

import com.google.common.annotations.VisibleForTesting;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.parser.ParseContext;
import com.google.idea.blaze.base.projectview.parser.ProjectViewParser;
import com.google.idea.blaze.base.projectview.section.ScalarSection;
import com.google.idea.blaze.base.projectview.section.ScalarSectionParser;
import com.google.idea.blaze.base.projectview.section.SectionKey;
import com.google.idea.blaze.base.projectview.section.SectionParser;
import com.intellij.pom.java.LanguageLevel;
import javax.annotation.Nullable;
import java.util.Locale;

/** Section to force the java language level used */
public class JavaLanguageLevelSection {

  public static final SectionKey<LanguageLevel, ScalarSection<LanguageLevel>> KEY =
      SectionKey.of("java_language_level");
  public static final SectionParser PARSER = new JavaLanguageLevelParser();

  public static LanguageLevel getLanguageLevel(
      ProjectViewSet projectViewSet, LanguageLevel defaultValue) {
    return projectViewSet
        .getScalarValue(KEY)
        .orElse(defaultValue);
  }

  @Nullable
  @VisibleForTesting
  static LanguageLevel getLanguageLevel(String level, @Nullable LanguageLevel defaultValue) {
    try {
      return LanguageLevel.valueOf(level.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      // fall through
    }

    final int numericVersion;
    try {
      // LanguageLevel.parse will return levels for strings containing numeric components
      // like FOO_11_FOO that should not be accepted by the plugin
      numericVersion = Integer.parseInt(level);
      if (numericVersion <= 0) {
        return defaultValue;
      }
    } catch (NumberFormatException e) {
      return defaultValue;
    }

    LanguageLevel languageLevel = LanguageLevel.parse(Integer.toString(numericVersion));
    if (languageLevel != null) {
      return languageLevel;
    }
    return defaultValue;
  }

  private static class JavaLanguageLevelParser extends ScalarSectionParser<LanguageLevel> {
    JavaLanguageLevelParser() {
      super(KEY, ':');
    }

    @Nullable
    @Override
    protected LanguageLevel parseItem(ProjectViewParser parser, ParseContext parseContext,
        String rest) {
      LanguageLevel languageLevel = getLanguageLevel(rest, null);
      if (languageLevel == null) {
        // Fall through to error handler
        parseContext.addError("Illegal java language level: " + rest);
        return null;
      } else {
        return languageLevel;
      }
    }

    @Override
    protected void printItem(StringBuilder sb, LanguageLevel value) {
      sb.append(value.toString());
    }

    @Override
    public ItemType getItemType() {
      return ItemType.Other;
    }
  }
}
