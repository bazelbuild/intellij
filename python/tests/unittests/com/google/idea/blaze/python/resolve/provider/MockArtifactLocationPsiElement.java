/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.python.resolve.provider;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;

/**
 * This is a fake {@link PsiElement} that is mostly dummy methods; just covering enough to support
 * the adjacent unit tests.
 */

public class MockArtifactLocationPsiElement implements PsiElement {

  private final File executionRelativeFile;

  public MockArtifactLocationPsiElement(String executationRelativePath) {
    this.executionRelativeFile = new File(executationRelativePath);
  }

  @Override
  public @NotNull Project getProject() throws PsiInvalidElementAccessException {
    throw new UnsupportedOperationException();
  }

  @Override
  public @NotNull Language getLanguage() {
    throw new UnsupportedOperationException();
  }

  @Override
  public PsiManager getManager() {
    throw new UnsupportedOperationException();
  }

  @Override
  public @NotNull PsiElement[] getChildren() {
    throw new UnsupportedOperationException();
  }

  @Override
  public PsiElement getParent() {
    return new MockArtifactLocationPsiElement(executionRelativeFile.getParent());
  }

  @Override
  public PsiElement getFirstChild() {
    throw new UnsupportedOperationException();
  }

  @Override
  public PsiElement getLastChild() {
    throw new UnsupportedOperationException();
  }

  @Override
  public PsiElement getNextSibling() {
    throw new UnsupportedOperationException();
  }

  @Override
  public PsiElement getPrevSibling() {
    throw new UnsupportedOperationException();
  }

  @Override
  public PsiFile getContainingFile() throws PsiInvalidElementAccessException {
    throw new UnsupportedOperationException();
  }

  @Override
  public TextRange getTextRange() {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getStartOffsetInParent() {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getTextLength() {
    throw new UnsupportedOperationException();
  }

  @Override
  public @Nullable PsiElement findElementAt(int i) {
    throw new UnsupportedOperationException();
  }

  @Override
  public @Nullable PsiReference findReferenceAt(int i) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getTextOffset() {
    throw new UnsupportedOperationException();
  }

  @Override
  public @NlsSafe String getText() {
    throw new UnsupportedOperationException();
  }

  @Override
  public char[] textToCharArray() {
    throw new UnsupportedOperationException();
  }

  @Override
  public PsiElement getNavigationElement() {
    throw new UnsupportedOperationException();
  }

  @Override
  public PsiElement getOriginalElement() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean textMatches(@NotNull @NonNls CharSequence charSequence) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean textMatches(@NotNull PsiElement psiElement) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean textContains(char c) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void accept(@NotNull PsiElementVisitor psiElementVisitor) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void acceptChildren(@NotNull PsiElementVisitor psiElementVisitor) {
    throw new UnsupportedOperationException();
  }

  @Override
  public PsiElement copy() {
    throw new UnsupportedOperationException();
  }

  @Override
  public PsiElement add(@NotNull PsiElement psiElement) throws IncorrectOperationException {
    throw new UnsupportedOperationException();
  }

  @Override
  public PsiElement addBefore(@NotNull PsiElement psiElement, @Nullable PsiElement psiElement1)
      throws IncorrectOperationException {
    throw new UnsupportedOperationException();
  }

  @Override
  public PsiElement addAfter(@NotNull PsiElement psiElement, @Nullable PsiElement psiElement1)
      throws IncorrectOperationException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void checkAdd(@NotNull PsiElement psiElement) throws IncorrectOperationException {
    throw new UnsupportedOperationException();
  }

  @Override
  public PsiElement addRange(PsiElement psiElement, PsiElement psiElement1)
      throws IncorrectOperationException {
    throw new UnsupportedOperationException();
  }

  @Override
  public PsiElement addRangeBefore(@NotNull PsiElement psiElement, @NotNull PsiElement psiElement1,
      PsiElement psiElement2) throws IncorrectOperationException {
    throw new UnsupportedOperationException();
  }

  @Override
  public PsiElement addRangeAfter(PsiElement psiElement, PsiElement psiElement1,
      PsiElement psiElement2) throws IncorrectOperationException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void delete() throws IncorrectOperationException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void checkDelete() throws IncorrectOperationException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void deleteChildRange(PsiElement psiElement, PsiElement psiElement1)
      throws IncorrectOperationException {
    throw new UnsupportedOperationException();
  }

  @Override
  public PsiElement replace(@NotNull PsiElement psiElement) throws IncorrectOperationException {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isValid() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isWritable() {
    throw new UnsupportedOperationException();
  }

  @Override
  public @Nullable PsiReference getReference() {
    throw new UnsupportedOperationException();
  }

  @Override
  public PsiReference[] getReferences() {
    throw new UnsupportedOperationException();
  }

  @Override
  public <T> @Nullable T getCopyableUserData(@NotNull Key<T> key) {
    throw new UnsupportedOperationException();
  }

  @Override
  public <T> void putCopyableUserData(@NotNull Key<T> key, @Nullable T t) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor psiScopeProcessor,
      @NotNull ResolveState resolveState, @Nullable PsiElement psiElement,
      @NotNull PsiElement psiElement1) {
    throw new UnsupportedOperationException();
  }

  @Override
  public @Nullable PsiElement getContext() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isPhysical() {
    throw new UnsupportedOperationException();
  }

  @Override
  public @NotNull GlobalSearchScope getResolveScope() {
    throw new UnsupportedOperationException();
  }

  @Override
  public @NotNull SearchScope getUseScope() {
    throw new UnsupportedOperationException();
  }

  @Override
  public ASTNode getNode() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isEquivalentTo(PsiElement psiElement) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Icon getIcon(int i) {
    throw new UnsupportedOperationException();
  }

  @Override
  public <T> @Nullable T getUserData(@NotNull Key<T> key) {
    throw new UnsupportedOperationException();
  }

  @Override
  public <T> void putUserData(@NotNull Key<T> key, @Nullable T t) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String toString() {
    return executionRelativeFile.getPath();
  }
}
