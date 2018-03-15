/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.kotlin.sync;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.parser.ParseContext;
import com.google.idea.blaze.base.projectview.parser.ProjectViewParser;
import com.google.idea.blaze.base.projectview.section.ScalarSection;
import com.google.idea.blaze.base.projectview.section.ScalarSectionParser;
import com.google.idea.blaze.base.projectview.section.SectionKey;
import com.google.idea.blaze.base.projectview.section.SectionParser;
import javax.annotation.Nullable;
import org.jetbrains.kotlin.config.LanguageVersion;

/** Project view sections for Kotlin. */
public final class BlazeKotlinLanguageVersionSection {
  private static final SectionKey<LanguageVersion, ScalarSection<LanguageVersion>>
      LANGUAGE_VERSION = new SectionKey<>("kotlin_language_version");

  private static final ScalarSectionParser<LanguageVersion> LANGUAGE_VERSION_PARSER =
      new ScalarSectionParser<LanguageVersion>(LANGUAGE_VERSION, ':') {
        @Nullable
        @Override
        protected LanguageVersion parseItem(
            ProjectViewParser parser, ParseContext parseContext, String rest) {
          LanguageVersion languageVersion = LanguageVersion.fromVersionString(rest);
          if (languageVersion == null) {
            parseContext.addError("Illegal kotlin language level: " + rest);
            return null;
          } else {
            return languageVersion;
          }
        }

        @Override
        public String quickDocs() {
          return "The kotlin language and api version.";
        }

        @Override
        protected void printItem(StringBuilder sb, LanguageVersion version) {
          sb.append(version.getVersionString());
        }

        @Override
        public ItemType getItemType() {
          return ItemType.Other;
        }
      };

  static final ImmutableList<SectionParser> PARSERS = ImmutableList.of(LANGUAGE_VERSION_PARSER);

  public static LanguageVersion getLanguageLevel(ProjectViewSet projectViewSet) {
    return projectViewSet.getScalarValue(LANGUAGE_VERSION).orElse(LanguageVersion.LATEST_STABLE);
  }
}
