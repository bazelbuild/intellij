/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
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
import com.google.idea.blaze.base.projectview.section.*;
import com.google.idea.blaze.kotlin.BlazeKotlin;
import org.jetbrains.kotlin.config.LanguageVersion;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.stream.Collectors;

public final class BlazeKotlinSections {
    private static final SectionKey<LanguageVersion, ScalarSection<LanguageVersion>>
            LANGUAGE_VERSION = new SectionKey<>("kotlin_language_version");
    private static final SectionKey<BlazeKotlinStdLib, ListSection<BlazeKotlinStdLib>>
            ADDITIONAL_STDLIBS = SectionKey.of("kotlin_additional_stdlibs");

    private static final ListSectionParser<BlazeKotlinStdLib> ADDITIONAL_STDLIBS_PARSER = new ListSectionParser<BlazeKotlinStdLib>(ADDITIONAL_STDLIBS) {
        final String VALID_LIBS = BlazeKotlinStdLib.OPTIONAL_STDLIBS.keySet().stream().collect(Collectors.joining(",", "(", ")"));

        @Nullable
        @Override
        protected BlazeKotlinStdLib parseItem(ProjectViewParser parser, ParseContext parseContext) {
            String libId = parseContext.current().text.trim();
            if (libId.isEmpty()) {
                return null;
            }
            BlazeKotlinStdLib blazeKotlinStdLib = BlazeKotlinStdLib.OPTIONAL_STDLIBS.get(libId);
            if (blazeKotlinStdLib == null) {
                parseContext.addError(libId + " is not a support stdlib chose from " + VALID_LIBS + ".");
                return null;
            }
            return blazeKotlinStdLib;
        }

        @Override
        protected void printItem(BlazeKotlinStdLib item, StringBuilder sb) {
            sb.append(item.id);
        }

        @Override
        public ItemType getItemType() {
            return ItemType.Other;
        }

        @Override
        public String quickDocs() {
            return "The additional kotlin standard libraries to make available from the kotlin compiler distribution used in the project";
        }
    };

    private static final ScalarSectionParser<LanguageVersion> LANGUAGE_VERSION_PARSER = new ScalarSectionParser<LanguageVersion>(LANGUAGE_VERSION, ':') {
        @Nullable
        @Override
        protected LanguageVersion parseItem(ProjectViewParser parser, ParseContext parseContext, String rest) {
            LanguageVersion languageVersion = LanguageVersion.fromVersionString(rest);
            if (languageVersion == null) {
                parseContext.addError("Illegal kotlin language level: " + rest);
                return null;
            } else {
                return languageVersion;
            }
        }

        @Nonnull
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

    static final ImmutableList<SectionParser> PARSERS = ImmutableList.of(
            LANGUAGE_VERSION_PARSER,
            ADDITIONAL_STDLIBS_PARSER
    );

    public static LanguageVersion getLanguageLevel(ProjectViewSet projectViewSet) {
        return projectViewSet.getScalarValue(LANGUAGE_VERSION).orElse(BlazeKotlin.DEFAULT_LANGUAGE_VERSION);
    }

    public static ImmutableList<BlazeKotlinStdLib> getAdditionalStdLibs(ProjectViewSet projectViewSet) {
        return projectViewSet.listItems(ADDITIONAL_STDLIBS).stream().distinct().collect(ImmutableList.toImmutableList());
    }
}
