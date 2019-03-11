/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.sdkcompat.cidr;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.jetbrains.cidr.lang.symbols.OCResolveContext;
import com.jetbrains.cidr.lang.symbols.OCSymbol;
import com.jetbrains.cidr.lang.symbols.cpp.OCFunctionSymbol;
import com.jetbrains.cidr.lang.symbols.cpp.OCSymbolWithQualifiedName;

/** Adapter to bridge different SDK versions. */
public class OCSymbolAdapter {
  public static PsiElement locateDefinition(OCSymbol symbol, Project project) {
    return symbol.locateDefinition(project);
  }

  public static OCSymbol getDefinitionSymbol(OCSymbolWithQualifiedName symbol, Project project) {
    return symbol.getDefinitionSymbol(project);
  }

  public static OCSymbolWithQualifiedName getResolvedOwner(
      OCFunctionSymbol symbol, Project project) {
    return symbol.getResolvedOwner(OCResolveContext.forSymbol(symbol, project));
  }
}
