/*
 * Copyright 2026 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.idea.blaze.base.projectview.section.sections;

import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.projectview.parser.ParseContext;
import com.google.idea.blaze.base.projectview.parser.ProjectViewParser;
import com.google.idea.blaze.base.projectview.section.ScalarSection;
import com.google.idea.blaze.base.projectview.section.SectionKey;
import com.google.idea.blaze.base.projectview.section.SectionParser;

import javax.annotation.Nullable;

/** "try_import" section. */
public class TryImportSection extends ImportSection {

    public static final SectionKey<WorkspacePath, ScalarSection<WorkspacePath>> KEY =
            SectionKey.of("try_import");

    public static final SectionParser PARSER = new TryImportSectionParser();

    private static class TryImportSectionParser extends ImportSectionParser {

        public TryImportSectionParser() {
            super(KEY, ' ');
        }

        @Nullable
        @Override
        protected WorkspacePath parseItem(
                ProjectViewParser parser, ParseContext parseContext, String text) {
            return parseItem(parser, parseContext, text, false);
        }
    }
}
