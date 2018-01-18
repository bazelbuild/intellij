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
import com.google.idea.blaze.base.command.BlazeFlags;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import org.jetbrains.kotlin.name.FqName;
import org.jetbrains.kotlin.psi.KtClass;
import org.jetbrains.kotlin.psi.KtNamedFunction;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.stream.Collectors;

/**
 * Shared Kotlin specific test utilities.
 */
public final class BlazeKotlinPsiUtils {
    /**
     * Walks up PSI tree, looking for a parent of the specified class. Stops searching when it reaches
     * a parent of type PsiDirectory.
     *
     * @param strict if true consider the starting element.
     */
    @Nullable
    private static <T extends PsiElement> T locateElementUpwards(PsiElement element, Class<T> psiClass, boolean strict) {
        element = strict ? element.getParent() : element;
        while (element != null && !(element instanceof PsiDirectory)) {
            if (psiClass.isInstance(element)) {
                return psiClass.cast(element);
            }
            element = element.getParent();
        }
        return null;
    }

    /**
     * Collects the parents of an element that are of a specific psi class type.
     *
     * @param strict if true consider the starting element.
     */
    @Nonnull
    private static <T extends PsiElement> ImmutableList<T> collectParents(
            PsiElement element, Class<T> psiClass, boolean strict) {
        ImmutableList.Builder<T> builder = ImmutableList.builder();
        T parent = locateElementUpwards(element, psiClass, strict);
        while (parent != null) {
            builder.add(parent);
            parent = locateElementUpwards(parent, psiClass, true);
        }
        return builder.build().reverse();
    }

    @Nonnull
    public static BlazeKotlinRunnable getRunnableFrom(@Nonnull PsiElement element) {
        ImmutableList<KtClass> containerClasses;

        KtNamedFunction ktFunction = BlazeKotlinPsiUtils.locateElementUpwards(element, KtNamedFunction.class, true);

        if (ktFunction != null) {
            containerClasses = BlazeKotlinPsiUtils.collectParents(ktFunction, KtClass.class, true);
        } else {
            containerClasses = BlazeKotlinPsiUtils.collectParents(element, KtClass.class, true);
        }
        return BlazeKotlinRunnable.of(containerClasses, ktFunction);
    }

    /**
     * Constructs a fully qualified name -- note: this does not mean the package name is included, just that if the runnable is for a test it includes the
     * nested suite class hierarchy. Other display naming strategies should be implemented for BDD frameworks, and for using something other than the technical
     * names in Jupiter.
     */
    @Nonnull
    public static String getSimpleDisplayName(BlazeKotlinRunnable runnable) {
        String displayName = runnable.containerClasses.stream().map(KtClass::getName).collect(Collectors.joining("."));
        if(runnable.function != null) {
            displayName += "." + runnable.function.getName();
        }
        return displayName;
    }

    @Nonnull
    public static String getTestFilterFlags(BlazeKotlinRunnable runnable) {
        FqName fqName;
        if (runnable.function != null) {
            fqName = runnable.function.getFqName();
        } else {
            fqName = runnable.containerClasses.get(runnable.containerClasses.size() - 1).getFqName();
        }

        if (fqName == null) {
            throw new RuntimeException("kt psi element does not have a fqn");
        } else {
            return BlazeFlags.TEST_FILTER + "=" + fqName.toString();
        }
    }
}
