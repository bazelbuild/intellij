package com.google.idea.sdkcompat.clion;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.jetbrains.cidr.cpp.actions.newFile.CPPNewFileHelperProvider;
import com.jetbrains.cidr.lang.actions.newFile.OCNewFileActionBase;
import com.jetbrains.cidr.lang.actions.newFile.OCNewFileHelper;
import com.jetbrains.cidr.lang.actions.newFile.OCNewFileHelperProvider;
import org.jetbrains.annotations.Nullable;

/**
 * Compat for {@link OCNewFileProjectModelHandler}.
 *
 * <p>#api203: OCNewFileHelper was renamed to OCNewFileProjectModelHandler with API change.
 */
public class OCNewFileProjectModelHandlerWrapper implements OCNewFileHelper {

  public static void replaceExtension(OCNewFileHelperProvider newProvider) {
    ExtensionPoint<OCNewFileHelperProvider> ep =
        Extensions.getRootArea().getExtensionPoint(OCNewFileHelperProvider.EP_NAME);
    for (OCNewFileHelperProvider filehelperProvider : ep.getExtensions()) {
      ep.unregisterExtension(filehelperProvider);
    }
    ep.registerExtension(newProvider);
  }

  // Class uses delegation instead of extension to use super.createHelper() in {@link CPPProvider}
  @Nullable private final OCNewFileHelper delegate;

  @Override
  public boolean initFromDataContext(DataContext var1) {
    return delegate.initFromDataContext(var1);
  }

  @Override
  public boolean initFromFile(PsiFile var1) {
    return delegate.initFromFile(var1);
  }

  @Override
  public String getDefaultClassPrefix() {
    return delegate.getDefaultClassPrefix();
  }

  @Override
  public boolean canChangeDir() {
    return delegate.canChangeDir();
  }

  @Override
  public DialogWrapper createDialog(
      OCNewFileActionBase<?>.CreateFileDialogBase var1,
      @Nullable PsiDirectory var2,
      @Nullable DataContext var3) {
    return delegate.createDialog(var1, var2, var3);
  }

  @Override
  public void doCreateFiles(
      Project var1,
      PsiDirectory var2,
      String[] var3,
      PsiFile[] var4,
      @Nullable DialogWrapper var5,
      @Nullable PsiFile var6) {
    delegate.doCreateFiles(var1, var2, var3, var4, var5, var6);
  }

  public boolean isAvailable(DataContext dataContext) {
    return true;
  }

  OCNewFileProjectModelHandlerWrapper(OCNewFileHelper delegate) {
    this.delegate = delegate;
  }

  public OCNewFileProjectModelHandlerWrapper() {
    this.delegate = null;
  }

  /**
   * Compat for {@link CPPNewFileProjectModelHandlerProvider}.
   *
   * <p>#api203. CPPNewFileHelperProvider was renamed to CPPNewFileProjectModelHandlerProvider.
   */
  public static class CPPProvider extends CPPNewFileHelperProvider {
    public OCNewFileProjectModelHandlerWrapper createHandler() {
      return new OCNewFileProjectModelHandlerWrapper(super.createHelper());
    }
  }
}
