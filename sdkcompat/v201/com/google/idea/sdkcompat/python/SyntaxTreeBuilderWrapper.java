package com.google.idea.sdkcompat.python;

import com.intellij.lang.PsiBuilder;
import java.util.function.Supplier;

/**
 * Compatibility wrapper to support that constructor of ParsingContext uses new interface
 * SyntaxTreeBuilder in 2020.2. #api201
 */
public interface SyntaxTreeBuilderWrapper extends Supplier<PsiBuilder> {

  static SyntaxTreeBuilderWrapper wrap(PsiBuilder builder) {
    return () -> builder;
  }

  /**
   * #api201: Compatibility wrapper for marker which is represented by a new interface in 2020.2.
   */
  interface MarkerWrapper extends Supplier<PsiBuilder.Marker> {

    static MarkerWrapper wrap(PsiBuilder.Marker marker) {
      return () -> marker;
    }
  }
}
