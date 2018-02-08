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
package com.google.idea.blaze.kotlin.run;

import com.google.common.collect.ImmutableList;
import org.jetbrains.kotlin.psi.KtClass;
import org.jetbrains.kotlin.psi.KtNamedFunction;

import javax.annotation.Nullable;

/**
 * Utility data class for holding the Kotlin PsiElements for a runnable entity.
 */
public class BlazeKotlinRunnable {
    /**
     * This is a list as a Junit Jupiter supports nested test containers.
     */
    public final ImmutableList<KtClass> containerClasses;

    /**
     * This is nullable as tests suites don't need a specific function to be runnable.
     */
    @Nullable
    public final KtNamedFunction function;

    private BlazeKotlinRunnable(ImmutableList<KtClass> containerClasses, @Nullable KtNamedFunction function) {
        this.function = function;
        this.containerClasses = containerClasses;
    }

    static BlazeKotlinRunnable of(ImmutableList<KtClass> containerClasses, @Nullable KtNamedFunction function) {
        return new BlazeKotlinRunnable(containerClasses, function);
    }
}
