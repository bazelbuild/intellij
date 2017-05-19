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
package com.google.idea.blaze.java.run.producers;

import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.junit.JUnitUtil;
import com.intellij.execution.testframework.TestsUIUtil;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.PsiElementProcessor;
import java.util.ArrayList;
import java.util.List;

/**
 * Cloned from PatternConfigurationProducer, stripped down to only contain
 * isMultipleElementsSelected.
 */
public class JUnitConfigurationUtil {
  protected static boolean isTestMethod(boolean checkAbstract, PsiElement psiElement) {
    return JUnitUtil.getTestMethod(psiElement, checkAbstract) != null;
  }

  public static boolean isMultipleElementsSelected(ConfigurationContext context) {
    final DataContext dataContext = context.getDataContext();
    if (TestsUIUtil.isMultipleSelectionImpossible(dataContext)) {
      return false;
    }
    final PsiElementProcessor.CollectElementsWithLimit<PsiElement> processor =
        new PsiElementProcessor.CollectElementsWithLimit<PsiElement>(2);
    final PsiElement[] locationElements = collectLocationElements(dataContext);
    if (locationElements != null) {
      collectTestMembers(locationElements, false, false, processor);
    } else {
      collectContextElements(dataContext, false, false, processor);
    }
    return processor.getCollection().size() > 1;
  }

  public static void collectTestMembers(
      PsiElement[] psiElements,
      boolean checkAbstract,
      boolean checkIsTest,
      PsiElementProcessor.CollectElements<PsiElement> collectingProcessor) {
    for (PsiElement psiElement : psiElements) {
      if (psiElement instanceof PsiClassOwner) {
        final PsiClass[] classes = ((PsiClassOwner) psiElement).getClasses();
        for (PsiClass aClass : classes) {
          if ((!checkIsTest && aClass.hasModifierProperty(PsiModifier.PUBLIC)
                  || checkIsTest && JUnitUtil.isTestClass(aClass))
              && !collectingProcessor.execute(aClass)) {
            return;
          }
        }
      } else if (psiElement instanceof PsiClass) {
        if ((!checkIsTest && ((PsiClass) psiElement).hasModifierProperty(PsiModifier.PUBLIC)
                || checkIsTest && JUnitUtil.isTestClass((PsiClass) psiElement))
            && !collectingProcessor.execute(psiElement)) {
          return;
        }
      } else if (psiElement instanceof PsiMethod) {
        if (checkIsTest
            && isTestMethod(checkAbstract, psiElement)
            && !collectingProcessor.execute(psiElement)) {
          return;
        }
        if (!checkIsTest) {
          final PsiClass containingClass = ((PsiMethod) psiElement).getContainingClass();
          if (containingClass != null
              && containingClass.hasModifierProperty(PsiModifier.PUBLIC)
              && !collectingProcessor.execute(psiElement)) {
            return;
          }
        }
      } else if (psiElement instanceof PsiDirectory) {
        final PsiPackage aPackage =
            JavaDirectoryService.getInstance().getPackage((PsiDirectory) psiElement);
        if (aPackage != null && !collectingProcessor.execute(aPackage)) {
          return;
        }
      }
    }
  }

  private static boolean collectContextElements(
      DataContext dataContext,
      boolean checkAbstract,
      boolean checkIsTest,
      PsiElementProcessor.CollectElements<PsiElement> processor) {
    PsiElement[] elements = LangDataKeys.PSI_ELEMENT_ARRAY.getData(dataContext);
    if (elements != null) {
      collectTestMembers(elements, checkAbstract, checkIsTest, processor);
      return true;
    } else {
      final VirtualFile[] files = CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(dataContext);
      if (files != null) {
        Project project = CommonDataKeys.PROJECT.getData(dataContext);
        if (project != null) {
          final PsiManager psiManager = PsiManager.getInstance(project);
          for (VirtualFile file : files) {
            final PsiFile psiFile = psiManager.findFile(file);
            if (psiFile instanceof PsiClassOwner) {
              collectTestMembers(
                  ((PsiClassOwner) psiFile).getClasses(), checkAbstract, checkIsTest, processor);
            }
          }
          return true;
        }
      }
    }
    return false;
  }

  private static PsiElement[] collectLocationElements(DataContext dataContext) {
    final Location<?>[] locations = Location.DATA_KEYS.getData(dataContext);
    if (locations != null) {
      List<PsiElement> elements = new ArrayList<PsiElement>();
      for (Location<?> location : locations) {
        final PsiElement psiElement = location.getPsiElement();
        elements.add(psiElement);
      }
      return elements.toArray(new PsiElement[elements.size()]);
    }
    return null;
  }
}
