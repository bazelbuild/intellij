package com.google.idea.sdkcompat.typescript;

import com.google.common.collect.ImmutableMap;
import com.intellij.lang.typescript.tsconfig.TypeScriptConfig;
import com.intellij.lang.typescript.tsconfig.TypeScriptConfigService;
import com.intellij.lang.typescript.tsconfig.TypeScriptConfigUtil;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;

/** Provides SDK compatibility shims for Typescript classes, available to IntelliJ UE and CLion. */
public class TypeScriptSDKCompat {
  private TypeScriptSDKCompat() {}

  /**
   * #api203: TypeScriptConfigUtil.getNearestParentConfigFiles was removed and
   * TypeScriptConfigServiceImpl.getNearestParentTsConfigs was added in 2021.1
   */
  public static Collection<? extends VirtualFile> getNearestParentTsConfigs(
      @Nullable VirtualFile scopeFile, ImmutableMap<VirtualFile, TypeScriptConfig> configs) {
    return TypeScriptConfigUtil.getNearestParentConfigFiles(scopeFile, configs.keySet());
  }

  /**
   * #api203: TypeScriptConfigService#getPreferableOrParentConfig was added in 2021.1, so we do
   * nothing in older versions.
   */
  public static TypeScriptConfig getPreferableOrParentConfig(
      TypeScriptConfigService impl, VirtualFile virtualFile) {
    return null;
  }

  /**
   * #api203: TypeScriptConfigService#getDirectIncludePreferableConfig was added in 2021.1, so we do
   * nothing in older versions.
   */
  public static TypeScriptConfig getDirectIncludePreferableConfig(
      TypeScriptConfigService impl, VirtualFile virtualFile) {
    return null;
  }

  /**
   * #api203: TypeScriptConfigService#getRootConfigFiles was added in 2021.1, so we do nothing in
   * older versions.
   */
  public static List<VirtualFile> getRootConfigFiles(TypeScriptConfigService impl) {
    return null;
  }

  /** #api203: TypeScriptConfigService#getConfigs was removed in 2021.1. */
  public static List<TypeScriptConfig> getConfigs(TypeScriptConfigService impl) {
    return impl.getConfigs();
  }

  /** #api203: TypeScriptConfigService#hasConfigs was removed in 2021.1. */
  public static boolean hasConfigs(TypeScriptConfigService impl) {
    return impl.hasConfigs();
  }
}
