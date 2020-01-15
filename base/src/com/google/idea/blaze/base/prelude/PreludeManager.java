package com.google.idea.blaze.base.prelude;

import com.google.idea.blaze.base.lang.buildfile.psi.BuildElement;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.util.Processor;

import javax.annotation.Nullable;

public abstract class PreludeManager {

  public static PreludeManager getInstance(Project project) {
    return ServiceManager.getService(project, PreludeManager.class);
  }

  public abstract boolean searchSymbolsInScope(Processor<BuildElement> processor,
      @Nullable PsiElement stopAtElement);

}
