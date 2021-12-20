package com.google.idea.sdkcompat.scala;

import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject;
import org.jetbrains.plugins.scala.runner.MyScalaMainMethodUtil;
import scala.Option;

/** Provides SDK compatibility shims for Scala classes, available to IntelliJ CE & UE. */
public class ScalaCompat {
  private ScalaCompat() {}

  /** #api212: Inline the call. Method location and signature changed in 2021.3 */
  public static Option<PsiMethod> findMainMethod(@NotNull ScObject obj) {
    return MyScalaMainMethodUtil.findScala2MainMethod(obj);
  }

  /** #api212: Inline the call. Method location and signature changed in 2021.3 */
  public static boolean hasMainMethod(@NotNull ScObject obj) {
    return MyScalaMainMethodUtil.hasScala2MainMethod(obj);
  }
}
