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
package com.google.idea.blaze.clwb.sync;

import com.google.idea.blaze.base.projectview.section.ScalarSection;
import com.google.idea.blaze.base.projectview.section.SectionKey;
import com.google.idea.blaze.base.projectview.section.SectionParser;
import com.google.idea.blaze.base.projectview.section.sections.BooleanSectionParser;

/** 'inject_debug_flags' section. */
public final class InjectDebugFlagsSection {

  public static final SectionKey<Boolean, ScalarSection<Boolean>> KEY = SectionKey.of("inject_debug_flags");

  public static final SectionParser PARSER = new BooleanSectionParser(KEY, """
        If set to true, inject a default set of debug build flags
        (like --compilation_mode=dbg, --copt=-g2, --strip=never, etc.) when building
        C/C++ run configurations in debug mode.
      """);

  private InjectDebugFlagsSection() {}
}
