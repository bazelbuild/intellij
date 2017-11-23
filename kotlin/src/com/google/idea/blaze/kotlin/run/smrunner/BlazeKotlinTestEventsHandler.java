/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.kotlin.run.smrunner;

import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.run.smrunner.BlazeTestEventsHandler;
import com.google.idea.blaze.base.run.smrunner.SmRunnerUtils;
import com.intellij.execution.Location;
import com.intellij.execution.testframework.sm.runner.SMTestLocator;
import com.intellij.openapi.project.Project;

import javax.annotation.Nullable;
import java.util.List;

import static com.google.idea.blaze.kotlin.run.BlazeKotlinPsiUtils.getRunnableFrom;
import static com.google.idea.blaze.kotlin.run.BlazeKotlinPsiUtils.getSimpleDisplayName;


/**
 * Provides kotlin-specific methods needed by the SM-runner test UI.
 */
public class BlazeKotlinTestEventsHandler implements BlazeTestEventsHandler {
    @Override
    public boolean handlesKind(@Nullable Kind kind) {
        return kind == Kind.KOTLIN_TEST;
    }

    @Override
    public SMTestLocator getTestLocator() {
        return BlazeKotlinTestLocator.INSTANCE;
    }

    @Nullable
    @Override
    public String getTestFilter(Project project, List<Location<?>> testLocations) {
        String filter = testLocations.stream()
                .map(Location::getPsiElement)
                .map(e -> getSimpleDisplayName(getRunnableFrom(e)))
                .reduce((a, b) -> a + "|" + b)
                .orElse(null);
        return filter != null ? String.format("%s=%s", BlazeFlags.TEST_FILTER, filter) : null;
    }

    @Override
    public String testDisplayName(@Nullable Kind kind, String rawName) {
        return rawName.replace(SmRunnerUtils.TEST_NAME_PARTS_SPLITTER, " ");
    }
}
