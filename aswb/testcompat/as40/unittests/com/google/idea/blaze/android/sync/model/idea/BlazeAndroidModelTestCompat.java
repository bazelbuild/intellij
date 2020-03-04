/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.sync.model.idea;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMap;
import com.intellij.mock.MockVirtualFile;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.JavaPsiFacadeImpl;
import com.intellij.psi.search.GlobalSearchScope;
import javax.annotation.Nullable;

/** Compat Class for {@link BlazeAndroidModelTest}. */
class BlazeAndroidModelTestCompat {
  private BlazeAndroidModelTestCompat() {}

  static class MockJavaPsiFacade extends JavaPsiFacadeImpl {
    private ImmutableMap<String, PsiClass> classes;
    private ImmutableMap<String, Long> timestamps;

    MockJavaPsiFacade(
        Project project, PsiManager psiManager, ImmutableCollection<String> classNames) {
      super(project);
      ImmutableMap.Builder<String, PsiClass> classesBuilder = ImmutableMap.builder();
      ImmutableMap.Builder<String, Long> timestampsBuilder = ImmutableMap.builder();
      for (String className : classNames) {
        VirtualFile virtualFile =
            new MockVirtualFile("/src/" + className.replace('.', '/') + ".java");
        PsiFile psiFile = mock(PsiFile.class);
        when(psiFile.getVirtualFile()).thenReturn(virtualFile);
        PsiClass psiClass = mock(PsiClass.class);
        when(psiClass.getContainingFile()).thenReturn(psiFile);
        classesBuilder.put(className, psiClass);
        timestampsBuilder.put(className, virtualFile.getTimeStamp());
      }
      classes = classesBuilder.build();
      timestamps = timestampsBuilder.build();
    }

    @Nullable
    @Override
    public PsiClass findClass(String qualifiedName, GlobalSearchScope scope) {
      if (scope.equals(GlobalSearchScope.projectScope(getProject()))) {
        return classes.get(qualifiedName);
      }
      return null;
    }

    long getTimestamp(String qualifiedName) {
      return timestamps.get(qualifiedName);
    }
  }
}
