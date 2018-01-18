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
package com.google.idea.blaze.kotlin.run.smrunner;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.run.smrunner.SmRunnerUtils;
import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.execution.testframework.JavaTestLocator;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.ClassUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.google.idea.blaze.base.run.smrunner.SmRunnerUtils.GENERIC_SUITE_PROTOCOL;
import static com.google.idea.blaze.base.run.smrunner.SmRunnerUtils.GENERIC_TEST_PROTOCOL;

/**
 * Locate Kotlin test packages / functions for test UI navigation.
 */
@SuppressWarnings("Duplicates")
public final class BlazeKotlinTestLocator extends JavaTestLocator {
    public static final BlazeKotlinTestLocator INSTANCE = new BlazeKotlinTestLocator();

    private BlazeKotlinTestLocator() {
    }


    @NotNull
    @Override
    @SuppressWarnings("rawtypes")
    public List<Location> getLocation(String protocol, String path, Project project, GlobalSearchScope scope) {
        List<Location> location = super.getLocation(protocol, path, project, scope);
        if (!location.isEmpty()) {
            return location;
        }
        switch (protocol) {
            case GENERIC_SUITE_PROTOCOL:
                // specs2 test.xml doesn't currently output the necessary information to location suites.
                return ImmutableList.of();
            case GENERIC_TEST_PROTOCOL:
                return findTestCase(project, path);
            default:
                return ImmutableList.of();
        }
    }

    @SuppressWarnings("rawtypes")
    private List<Location> findTestCase(Project project, String path) {
        String[] parts = path.split(SmRunnerUtils.TEST_NAME_PARTS_SPLITTER, 2);
        if (parts.length < 2)
            return ImmutableList.of();
        String className = parts[0], testName = parts[1];
        PsiClass psiClass = ClassUtil.findPsiClass(PsiManager.getInstance(project), className);
        return ImmutableList.of(new PsiLocation(psiClass.findMethodsByName(testName, false)[0]));
    }
}
