package com.google.idea.sdkcompat.cidr;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.jetbrains.cidr.lang.OCLanguageKind;
import com.jetbrains.cidr.lang.preprocessor.OCCompilerMacros;
import com.jetbrains.cidr.lang.preprocessor.OCInclusionContext;
import com.jetbrains.cidr.lang.workspace.compiler.OCCompilerFeatures;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/** Adapter to bridge different SDK versions. */
public abstract class OCCompilerMacrosAdapter extends OCCompilerMacros {

  // v171
  protected abstract void fillFileMacros(
      @NotNull OCInclusionContext context, @NotNull PsiFile sourceFile);

  // v171
  protected void addAllFeatures(
      Map<String, String> collection, Map<OCCompilerFeatures.Type<?>, ?> features) {}

  // v171
  public static void fillSubstitutions(OCInclusionContext context, String text) {}

  // v171
  public void enableClangFeatures(
      @NotNull OCInclusionContext context, @NotNull Map<String, String> features) {}

  // v171
  public void enableClangExtensions(
      @NotNull OCInclusionContext context, @NotNull Map<String, String> extensions) {}

  // v172
  public abstract String getAllDefines(OCLanguageKind kind, VirtualFile vf);
}
