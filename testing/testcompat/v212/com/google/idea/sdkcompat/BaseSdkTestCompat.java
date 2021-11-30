/*
 * Copyright 2021 The Bazel Authors. All rights reserved.
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
package com.google.idea.sdkcompat;

import java.util.List;

import com.intellij.codeInsight.daemon.impl.AnnotationHolderImpl;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationSession;
import com.intellij.lang.annotation.Annotator;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;

/**
 * Provides SDK compatibility shims for base plugin API classes, available to all IDEs during
 * test-time.
 */
public final class BaseSdkTestCompat {
  private BaseSdkTestCompat() {}

  /** #api212: inline into test cases */
  public static List<Annotation> testAnnotator(Annotator annotator, PsiElement... elements) {
    PsiFile file = elements[0].getContainingFile();
    AnnotationHolderImpl holder = new AnnotationHolderImpl(new AnnotationSession(file));
    for (PsiElement element : elements) {
      holder.runAnnotatorWithContext(element, annotator);
    }
    holder.assertAllAnnotationsCreated();
    return ContainerUtil.immutableList(holder);
  }
}
