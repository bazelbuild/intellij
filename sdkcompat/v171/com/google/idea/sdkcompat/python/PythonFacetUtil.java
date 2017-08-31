package com.google.idea.sdkcompat.python;

import com.intellij.facet.FacetTypeId;
import com.intellij.openapi.projectRoots.Sdk;
import com.jetbrains.python.facet.LibraryContributingFacet;
import com.jetbrains.python.facet.PythonFacet;
import com.jetbrains.python.facet.PythonFacetType;
import javax.annotation.Nullable;

/**
 * This class is a hack to get around Python SDK incompatibilities. Provides a consistent API for
 * both IntelliJ and CLion.
 */
public class PythonFacetUtil {
  public static FacetTypeId<PythonFacet> getFacetId() {
    return PythonFacet.ID;
  }

  public static PythonFacetType getTypeInstance() {
    return PythonFacetType.getInstance();
  }

  @Nullable
  public static Sdk getSdk(LibraryContributingFacet<?> facet) {
    if (!(facet instanceof PythonFacet)) {
      return null;
    }
    PythonFacet pythonFacet = (PythonFacet) facet;
    return pythonFacet.getConfiguration().getSdk();
  }
}
