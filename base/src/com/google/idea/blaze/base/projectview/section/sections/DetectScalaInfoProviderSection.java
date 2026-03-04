/*
 * Copyright 2026 The Bazel Authors. All rights reserved.
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

/** 'detect_scala_info_provider' section. */
public class DetectScalaInfoProviderSection {
    public static final SectionKey<Boolean, ScalarSection<Boolean>> KEY =
            SectionKey.of("detect_scala_info_provider");

    public static final SectionParser PARSER = new BooleanSectionParser(
            KEY,
            """
                    Set to false if using an older rules_scala version (e.g. @io_bazel_rules_scala) that \
                    does not export ScalaInfo from scala/providers.bzl. When false, Scala targets are \
                    detected using the rule-name prefix heuristic (ctx.rule.kind.startswith("scala")) \
                    instead of the ScalaInfo provider. By default this is true.
                    """);
}
