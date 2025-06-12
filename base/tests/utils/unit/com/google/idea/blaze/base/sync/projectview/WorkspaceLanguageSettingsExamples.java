/*
 * Copyright 2025 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.sync.projectview;

import com.google.common.collect.Sets;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspaceType;

/**
 * A collection of pre-built {@link WorkspaceLanguageSettings} which are frequently used in tests.
 */
public class WorkspaceLanguageSettingsExamples {
    public static final class SingleLanguage {
        public static final WorkspaceLanguageSettings GO = new WorkspaceLanguageSettings(
                WorkspaceType.GO, Sets.immutableEnumSet(LanguageClass.GO));
        public static final WorkspaceLanguageSettings JAVA = new WorkspaceLanguageSettings(
                WorkspaceType.JAVA, Sets.immutableEnumSet(LanguageClass.JAVA));
        public static final WorkspaceLanguageSettings JAVASCRIPT = new WorkspaceLanguageSettings(
                WorkspaceType.JAVASCRIPT, Sets.immutableEnumSet(LanguageClass.JAVASCRIPT));
    }
}
