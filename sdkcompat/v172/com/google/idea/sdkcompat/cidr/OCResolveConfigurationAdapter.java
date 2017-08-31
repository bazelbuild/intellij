package com.google.idea.sdkcompat.cidr;

import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.cidr.lang.OCLanguageKind;
import com.jetbrains.cidr.lang.preprocessor.OCCompilerMacros;
import com.jetbrains.cidr.lang.workspace.OCIncludeMap;
import com.jetbrains.cidr.lang.workspace.OCResolveConfiguration;
import com.jetbrains.cidr.lang.workspace.compiler.OCCompilerFeatures.Type;
import com.jetbrains.cidr.lang.workspace.compiler.OCCompilerSettings;
import com.jetbrains.cidr.toolchains.OCCompilerSettingsBackedByCompilerCache;
import java.util.Collections;
import java.util.Map;
import javax.annotation.Nullable;

/** Adapter to bridge different SDK versions. */
public abstract class OCResolveConfigurationAdapter extends UserDataHolderBase
    implements OCResolveConfiguration {
  /* v162/v163 */
  public abstract VirtualFile getPrecompiledHeader();

  /* v162/v163 */
  public abstract OCLanguageKind getPrecompiledLanguageKind();

  /* v171 */
  public abstract OCCompilerMacros getCompilerMacros();

  @Override
  public Map<Type<?>, ?> getCompilerFeatures(
      OCLanguageKind kind, @Nullable VirtualFile virtualFile) {
    OCCompilerSettings compilerSettings = getCompilerSettings();
    if (!(compilerSettings instanceof OCCompilerSettingsBackedByCompilerCache)) {
      return Collections.emptyMap();
    }

    OCCompilerSettingsBackedByCompilerCache backedCompilerSettings =
        (OCCompilerSettingsBackedByCompilerCache) compilerSettings;
    return backedCompilerSettings.getCompilerFeatures(kind, virtualFile);
  }

  @Override
  public OCIncludeMap getIncludeMap() {
    return OCIncludeMap.EMPTY;
  }
}
