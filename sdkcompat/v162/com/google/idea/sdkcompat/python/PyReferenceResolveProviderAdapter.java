package com.google.idea.sdkcompat.python;

import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.resolve.PyReferenceResolveProvider;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.types.TypeEvalContext;
import java.util.List;

/** Adapter to bridge different SDK versions. */
public interface PyReferenceResolveProviderAdapter extends PyReferenceResolveProvider {

  @Override
  default List<RatedResolveResult> resolveName(PyQualifiedExpression element) {
    TypeEvalContext context = TypeEvalContext.codeInsightFallback(element.getProject());
    return resolveName(element, context);
  }

  List<RatedResolveResult> resolveName(PyQualifiedExpression element, TypeEvalContext context);
}
