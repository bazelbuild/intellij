/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.sdkcompat.psi;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Pass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.SearchScope;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.rename.RenameDialog;
import com.intellij.refactoring.rename.RenamePsiElementProcessor;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.MultiMap;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Delegates to another {@link RenamePsiElementProcessor}, dependent on the {@link PsiElement}
 * context. Methods which don't pass through a {@link PsiElement} are delegated via a hacky state
 * storage, relying on a previous method call.
 */
public abstract class DelegatingRenamePsiElementProcessor extends RenamePsiElementProcessor {

  private volatile RenamePsiElementProcessor baseProcessor;

  @Override
  public boolean isInplaceRenameSupported() {
    return baseProcessor != null
        ? baseProcessor.isInplaceRenameSupported()
        : super.isInplaceRenameSupported();
  }

  @Override
  public boolean forcesShowPreview() {
    return baseProcessor != null ? baseProcessor.forcesShowPreview() : super.forcesShowPreview();
  }

  @Nullable
  protected abstract RenamePsiElementProcessor getDelegate(PsiElement element);

  @Nullable
  private RenamePsiElementProcessor getDelegateAndStoreState(PsiElement element) {
    RenamePsiElementProcessor delegate = getDelegate(element);
    baseProcessor = delegate;
    return delegate;
  }

  @Override
  public boolean canProcessElement(PsiElement element) {
    RenamePsiElementProcessor delegate = getDelegateAndStoreState(element);
    return delegate != null && delegate.canProcessElement(element);
  }

  @Override
  public RenameDialog createRenameDialog(
      Project project, PsiElement element, PsiElement nameSuggestionContext, Editor editor) {
    RenamePsiElementProcessor processor = getDelegateAndStoreState(element);
    if (processor != null) {
      return processor.createRenameDialog(project, element, nameSuggestionContext, editor);
    } else {
      return super.createRenameDialog(project, element, nameSuggestionContext, editor);
    }
  }

  @Override
  public void renameElement(
      PsiElement element,
      String newName,
      UsageInfo[] usages,
      @Nullable RefactoringElementListener listener)
      throws IncorrectOperationException {
    RenamePsiElementProcessor processor = getDelegateAndStoreState(element);
    if (processor != null) {
      processor.renameElement(element, newName, usages, listener);
    } else {
      super.renameElement(element, newName, usages, listener);
    }
  }

  @Override
  public Collection<PsiReference> findReferences(PsiElement element) {
    RenamePsiElementProcessor processor = getDelegateAndStoreState(element);
    if (processor != null) {
      return processor.findReferences(element);
    } else {
      return super.findReferences(element);
    }
  }

  @Override
  public Collection<PsiReference> findReferences(
      PsiElement element, boolean searchInCommentsAndStrings) {
    RenamePsiElementProcessor processor = getDelegateAndStoreState(element);
    if (processor != null) {
      return processor.findReferences(element, searchInCommentsAndStrings);
    } else {
      return super.findReferences(element, searchInCommentsAndStrings);
    }
  }

  @Nullable
  @Override
  public Pair<String, String> getTextOccurrenceSearchStrings(PsiElement element, String newName) {
    RenamePsiElementProcessor processor = getDelegateAndStoreState(element);
    if (processor != null) {
      return processor.getTextOccurrenceSearchStrings(element, newName);
    } else {
      return super.getTextOccurrenceSearchStrings(element, newName);
    }
  }

  @Nullable
  @Override
  public String getQualifiedNameAfterRename(PsiElement element, String newName, boolean nonJava) {
    RenamePsiElementProcessor processor = getDelegateAndStoreState(element);
    if (processor != null) {
      return processor.getQualifiedNameAfterRename(element, newName, nonJava);
    } else {
      return super.getQualifiedNameAfterRename(element, newName, nonJava);
    }
  }

  @Override
  public void prepareRenaming(
      PsiElement element, String newName, Map<PsiElement, String> allRenames) {
    RenamePsiElementProcessor processor = getDelegateAndStoreState(element);
    if (processor != null) {
      processor.prepareRenaming(element, newName, allRenames);
    } else {
      super.prepareRenaming(element, newName, allRenames);
    }
  }

  @Override
  public void prepareRenaming(
      PsiElement element, String newName, Map<PsiElement, String> allRenames, SearchScope scope) {
    RenamePsiElementProcessor processor = getDelegateAndStoreState(element);
    if (processor != null) {
      processor.prepareRenaming(element, newName, allRenames, scope);
    } else {
      super.prepareRenaming(element, newName, allRenames, scope);
    }
  }

  @Override
  public void findExistingNameConflicts(
      PsiElement element, String newName, MultiMap<PsiElement, String> conflicts) {
    RenamePsiElementProcessor processor = getDelegateAndStoreState(element);
    if (processor != null) {
      processor.findExistingNameConflicts(element, newName, conflicts);
    } else {
      super.findExistingNameConflicts(element, newName, conflicts);
    }
  }

  @Override
  public void findExistingNameConflicts(
      PsiElement element,
      String newName,
      MultiMap<PsiElement, String> conflicts,
      Map<PsiElement, String> allRenames) {
    RenamePsiElementProcessor processor = getDelegateAndStoreState(element);
    if (processor != null) {
      processor.findExistingNameConflicts(element, newName, conflicts, allRenames);
    } else {
      super.findExistingNameConflicts(element, newName, conflicts, allRenames);
    }
  }

  @Nullable
  @Override
  public Runnable getPostRenameCallback(
      PsiElement element, String newName, RefactoringElementListener elementListener) {
    RenamePsiElementProcessor processor = getDelegateAndStoreState(element);
    if (processor != null) {
      return processor.getPostRenameCallback(element, newName, elementListener);
    } else {
      return super.getPostRenameCallback(element, newName, elementListener);
    }
  }

  @Nullable
  @Override
  public String getHelpID(PsiElement element) {
    RenamePsiElementProcessor processor = getDelegateAndStoreState(element);
    if (processor != null) {
      return processor.getHelpID(element);
    } else {
      return super.getHelpID(element);
    }
  }

  @Override
  public boolean isToSearchInComments(PsiElement element) {
    RenamePsiElementProcessor processor = getDelegateAndStoreState(element);
    if (processor != null) {
      return processor.isToSearchInComments(element);
    } else {
      return super.isToSearchInComments(element);
    }
  }

  @Override
  public void setToSearchInComments(PsiElement element, boolean enabled) {
    RenamePsiElementProcessor processor = getDelegateAndStoreState(element);
    if (processor != null) {
      processor.setToSearchInComments(element, enabled);
    } else {
      super.setToSearchInComments(element, enabled);
    }
  }

  @Override
  public boolean isToSearchForTextOccurrences(PsiElement element) {
    RenamePsiElementProcessor processor = getDelegateAndStoreState(element);
    if (processor != null) {
      return processor.isToSearchForTextOccurrences(element);
    } else {
      return super.isToSearchForTextOccurrences(element);
    }
  }

  @Override
  public void setToSearchForTextOccurrences(PsiElement element, boolean enabled) {
    RenamePsiElementProcessor processor = getDelegateAndStoreState(element);
    if (processor != null) {
      processor.setToSearchForTextOccurrences(element, enabled);
    } else {
      super.setToSearchForTextOccurrences(element, enabled);
    }
  }

  @Override
  public boolean showRenamePreviewButton(PsiElement psiElement) {
    RenamePsiElementProcessor processor = getDelegateAndStoreState(psiElement);
    if (processor != null) {
      return processor.showRenamePreviewButton(psiElement);
    } else {
      return super.showRenamePreviewButton(psiElement);
    }
  }

  @Nullable
  @Override
  public PsiElement substituteElementToRename(PsiElement element, @Nullable Editor editor) {
    RenamePsiElementProcessor processor = getDelegateAndStoreState(element);
    if (processor != null) {
      return processor.substituteElementToRename(element, editor);
    } else {
      return super.substituteElementToRename(element, editor);
    }
  }

  @Override
  public void substituteElementToRename(
      PsiElement element, Editor editor, Pass<PsiElement> renameCallback) {
    RenamePsiElementProcessor processor = getDelegateAndStoreState(element);
    if (processor != null) {
      processor.substituteElementToRename(element, editor, renameCallback);
    } else {
      super.substituteElementToRename(element, editor, renameCallback);
    }
  }

  @Override
  public void findCollisions(
      PsiElement element,
      String newName,
      Map<? extends PsiElement, String> allRenames,
      List<UsageInfo> result) {
    RenamePsiElementProcessor processor = getDelegateAndStoreState(element);
    if (processor != null) {
      processor.findCollisions(element, newName, allRenames, result);
    } else {
      super.findCollisions(element, newName, allRenames, result);
    }
  }

  @Nullable
  @Override
  public PsiElement getElementToSearchInStringsAndComments(PsiElement element) {
    RenamePsiElementProcessor processor = getDelegateAndStoreState(element);
    if (processor != null) {
      return processor.getElementToSearchInStringsAndComments(element);
    } else {
      return super.getElementToSearchInStringsAndComments(element);
    }
  }
}
