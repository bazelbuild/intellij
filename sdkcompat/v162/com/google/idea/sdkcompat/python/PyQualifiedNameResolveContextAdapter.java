package com.google.idea.sdkcompat.python;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.jetbrains.python.psi.resolve.QualifiedNameResolveContext;
import javax.annotation.Nullable;

/** Adapter to bridge different SDK versions. */
public class PyQualifiedNameResolveContextAdapter extends QualifiedNameResolveContext {

  private final QualifiedNameResolveContext delegate;

  PyQualifiedNameResolveContextAdapter(QualifiedNameResolveContext delegate) {
    this.delegate = delegate;
  }

  @Override
  public void copyFrom(QualifiedNameResolveContext context) {
    delegate.copyFrom(context);
  }

  @Override
  public void setFromElement(PsiElement element) {
    delegate.setFromElement(element);
  }

  @Override
  public void setFromModule(Module module) {
    delegate.setFromModule(module);
  }

  @Override
  public void setFromSdk(Project project, Sdk sdk) {
    delegate.setFromSdk(project, sdk);
  }

  @Override
  public void setSdk(Sdk sdk) {
    delegate.setSdk(sdk);
  }

  @Override
  @Nullable
  public Module getModule() {
    return delegate.getModule();
  }

  @Override
  public boolean isValid() {
    return delegate.isValid();
  }

  @Override
  @Nullable
  public PsiFile getFootholdFile() {
    return delegate.getFootholdFile();
  }

  @Override
  public PsiManager getPsiManager() {
    return delegate.getPsiManager();
  }

  @Override
  public Project getProject() {
    return delegate.getProject();
  }

  @Override
  public Sdk getSdk() {
    return delegate.getSdk();
  }
}
