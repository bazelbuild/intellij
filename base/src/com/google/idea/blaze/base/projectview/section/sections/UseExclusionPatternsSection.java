/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.projectview.section.sections;

import com.google.idea.blaze.base.projectview.section.ScalarSection;
import com.google.idea.blaze.base.projectview.section.SectionKey;
import com.google.idea.blaze.base.projectview.section.SectionParser;

/** 'use_exclusion_pattern' section. */
public class UseExclusionPatternsSection {
    public static final SectionKey<Boolean, ScalarSection<Boolean>> KEY =
            SectionKey.of("use_exclusion_patterns");

    public static final SectionParser PARSER = new BooleanSectionParser(
            KEY,
            """
                    Add an exclusion pattern for all directories that are supposed to be excluded from the project.
                    It might significantly improve first import performance, but if your project contains files
                    that are typically ignored (`bazel-bin`, `bazel-out`, `.ijwb` etc.) they will be excluded, even
                    if they do noy lay in the root directory. In these cases, please disable exclusion patterns (set
                    to false). By default the exclusion patterns are enabled. For more info please check
                    https://www.jetbrains.com/help/idea/content-roots.html#exclude_folders
                    """);


}


