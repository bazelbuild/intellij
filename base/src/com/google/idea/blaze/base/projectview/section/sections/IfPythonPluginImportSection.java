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
package com.google.idea.blaze.base.projectview.section.sections;

import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.plugin.PluginUtils;
import com.google.idea.blaze.base.projectview.parser.ParseContext;
import com.google.idea.blaze.base.projectview.parser.ProjectViewParser;
import com.google.idea.blaze.base.projectview.section.ScalarSection;
import com.google.idea.blaze.base.projectview.section.SectionKey;
import com.google.idea.blaze.base.projectview.section.SectionParser;
import com.google.idea.blaze.base.sync.BlazeSyncPlugin;
import com.intellij.openapi.diagnostic.Logger;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>Only parses the imported section if the python plugin is provided in the IDE.</p>
 *
 * <p>This is useful in the situation where your wider project requires Python settings to be made,
 * but some users are not working with Python and so won't have the Python plugin installed.</p>
 */

public class IfPythonPluginImportSection extends ImportSection {

    private static final Logger logger = Logger.getInstance(IfPythonPluginImportSection.class);

    public static final SectionKey<WorkspacePath, ScalarSection<WorkspacePath>> KEY =
            SectionKey.of("if_python_plugin_import");

    public static final SectionParser PARSER = new IfPythonPluginImportSectionParser();

    private static class IfPythonPluginImportSectionParser extends ImportSectionParser {

        public IfPythonPluginImportSectionParser() {
            super(KEY, ' ');
        }

        @Nullable
        @Override
        protected WorkspacePath parseItem(
                ProjectViewParser parser, ParseContext parseContext, String text) {

            Set<String> requiredPythonPluginIds = Arrays.stream(BlazeSyncPlugin.EP_NAME.getExtensions())
                    .flatMap(syncPlugin -> syncPlugin.getRequiredExternalPluginIds(List.of(LanguageClass.PYTHON)).stream())
                    .collect(Collectors.toUnmodifiableSet());

            Set<String> missingPythonPluginIds = requiredPythonPluginIds.stream()
                    .filter(pluginId -> !PluginUtils.isPluginEnabled(pluginId))
                    .collect(Collectors.toUnmodifiableSet());

            if (!missingPythonPluginIds.isEmpty()) {
                logger.info("`if_python_plugin_import` is skipped as there are missing python plugins");
                return new WorkspacePath(text);
            }

            return super.parseItem(parser, parseContext, text);
        }
    }

}
