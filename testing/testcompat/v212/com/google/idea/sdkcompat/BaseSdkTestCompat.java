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

import com.intellij.codeInsight.daemon.impl.AnnotationHolderImpl;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationSession;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.components.ComponentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.intellij.util.containers.ContainerUtil;
import java.util.List;
import org.picocontainer.MutablePicoContainer;

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

  /** #api212: inline into ServiceHelper */
  public static void unregisterComponent(ComponentManager componentManager, String name) {
    ((MutablePicoContainer) componentManager.getPicoContainer()).unregisterComponent(name);
  }

  /** #api213: inline into tests */
  public static TestFixtureBuilder<IdeaProjectTestFixture> createLightFixtureBuilder(
      IdeaTestFixtureFactory factory, String projectName) {
    return factory.createLightFixtureBuilder();
  }
}
