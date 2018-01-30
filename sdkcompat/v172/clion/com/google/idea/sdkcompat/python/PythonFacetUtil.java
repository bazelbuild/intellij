/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.sdkcompat.python;

import com.intellij.facet.FacetTypeId;
import com.intellij.openapi.projectRoots.Sdk;
import com.jetbrains.python.facet.LibraryContributingFacet;
import com.jetbrains.python.minor.facet.PythonFacet;
import com.jetbrains.python.minor.facet.PythonFacetType;
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
