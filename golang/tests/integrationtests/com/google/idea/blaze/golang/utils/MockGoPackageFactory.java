package com.google.idea.blaze.golang.utils;

import com.goide.project.GoPackageFactory;
import com.goide.psi.GoFile;
import com.goide.psi.impl.GoPackage;
import com.intellij.psi.PsiDirectory;
import java.util.HashMap;
import org.jetbrains.annotations.NotNull;
import java.util.Map;
import org.jetbrains.annotations.Nullable;


/**
 * A testing facility to mock go package resolution.
 * When resolving Go symbols, the Go plugin creates artificial Go packages from individual files,
 * in an attempt to extract the go import path (e.g. golang.org/x/tools) from them.
 *
 * For most packages, this process is fine.
 * However, we may not want to import entire copies of the Go standard library
 * or other third party packages, just to fuel that resolution in a test case.
 *
 * This factory can be used in the {@link GoPackageFactory} extension point,
 * hence allowing us to mock the GoFile -> GoPackage translation with prebuilt go packages.
 *
 * Implementation-wise, everything is stored in a regular map with {@link String} keys,
 * and each function has its own interpretation of how to extract that String from its arguments.
 */
public class MockGoPackageFactory implements GoPackageFactory {

  Map<String, GoPackage> packages = new HashMap<>();

  public void put(String packageName, GoPackage pkgs) {
    packages.put(packageName, pkgs);
  }

  @Override
  public @Nullable GoPackage createPackage(@NotNull String packageName, PsiDirectory ... psiDirectories) {
    return packages.get(packageName);
  }

  @Override
  public @Nullable GoPackage createPackage(@NotNull GoFile goFile) {
    return packages.get(goFile.getCanonicalPackageName());
  }
}
