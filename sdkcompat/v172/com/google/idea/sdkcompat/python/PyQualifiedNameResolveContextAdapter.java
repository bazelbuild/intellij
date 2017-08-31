package com.google.idea.sdkcompat.python;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.jetbrains.python.psi.resolve.PyQualifiedNameResolveContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Adapter to bridge different SDK versions. */
public class PyQualifiedNameResolveContextAdapter implements PyQualifiedNameResolveContext {

  private final PyQualifiedNameResolveContext delegate;

  PyQualifiedNameResolveContextAdapter(PyQualifiedNameResolveContext delegate) {
    this.delegate = delegate;
  }

  @Nullable
  @Override
  public PsiElement getFoothold() {
    return delegate.getFoothold();
  }

  @Override
  public int getRelativeLevel() {
    return delegate.getRelativeLevel();
  }

  @Nullable
  @Override
  public Sdk getSdk() {
    return delegate.getSdk();
  }

  @Nullable
  @Override
  public Module getModule() {
    return delegate.getModule();
  }

  @NotNull
  @Override
  public Project getProject() {
    return delegate.getProject();
  }

  @Override
  public boolean getWithoutRoots() {
    return delegate.getWithoutRoots();
  }

  @Override
  public boolean getWithoutForeign() {
    return delegate.getWithoutForeign();
  }

  @Override
  public boolean getWithoutStubs() {
    return delegate.getWithoutStubs();
  }

  @NotNull
  @Override
  public PsiManager getPsiManager() {
    return delegate.getPsiManager();
  }

  @Override
  public boolean getWithMembers() {
    return delegate.getWithMembers();
  }

  @Override
  public boolean getWithPlainDirectories() {
    return delegate.getWithPlainDirectories();
  }

  @Override
  public boolean getVisitAllModules() {
    return delegate.getVisitAllModules();
  }

  @Nullable
  @Override
  public Sdk getEffectiveSdk() {
    return delegate.getEffectiveSdk();
  }

  @Override
  public boolean isValid() {
    return delegate.isValid();
  }

  @Nullable
  @Override
  public PsiFile getFootholdFile() {
    return delegate.getFootholdFile();
  }

  @Nullable
  @Override
  public PsiDirectory getContainingDirectory() {
    return delegate.getContainingDirectory();
  }

  @NotNull
  @Override
  public PyQualifiedNameResolveContext copyWithoutForeign() {
    return delegate.copyWithoutForeign();
  }

  @NotNull
  @Override
  public PyQualifiedNameResolveContext copyWithMembers() {
    return delegate.copyWithMembers();
  }

  @NotNull
  @Override
  public PyQualifiedNameResolveContext copyWithPlainDirectories() {
    return delegate.copyWithPlainDirectories();
  }

  @NotNull
  @Override
  public PyQualifiedNameResolveContext copyWithRelative(int i) {
    return delegate.copyWithRelative(i);
  }

  @NotNull
  @Override
  public PyQualifiedNameResolveContext copyWithoutRoots() {
    return delegate.copyWithoutRoots();
  }

  @NotNull
  @Override
  public PyQualifiedNameResolveContext copyWithRoots() {
    return delegate.copyWithRoots();
  }

  @NotNull
  @Override
  public PyQualifiedNameResolveContext copyWithoutStubs() {
    return delegate.copyWithoutStubs();
  }
}
