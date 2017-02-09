/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.lang.buildfile.validation;

import com.google.idea.blaze.base.lang.buildfile.psi.Argument;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildElement;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.lang.buildfile.psi.FuncallExpression;
import com.google.idea.blaze.base.lang.buildfile.psi.FunctionStatement;
import com.google.idea.blaze.base.lang.buildfile.psi.LoadStatement;
import com.google.idea.blaze.base.lang.buildfile.psi.LoadedSymbol;
import com.google.idea.blaze.base.lang.buildfile.psi.ParameterList;
import com.google.idea.blaze.base.lang.buildfile.psi.StringLiteral;
import com.google.idea.blaze.base.lang.buildfile.references.LabelReference;
import com.intellij.psi.PsiElement;
import java.util.Arrays;
import javax.annotation.Nullable;

/**
 * Error annotations for load statements, post parsing.
 *
 * <p>This has been turned off because it's unusable. BuildFile is re-parsed *every* time it's
 * touched, and is never cached. Until this is fixed, we can't run any annotators touching the file.
 *
 * <p>One option: try moving all expensive checks to 'visitFile', so they're not run in parallel
 */
public class LoadErrorAnnotator extends BuildAnnotator {

  @Override
  public void visitLoadStatement(LoadStatement node) {
    BuildElement[] children = node.buildElementChildren();
    //    StringLiteral[] strings = node..getChildStrings();
    if (children.length == 0) {
      return;
    }
    PsiElement skylarkRef = getSkylarkRef(children[0]);
    if (skylarkRef == null) {
      markError(children[0], "Cannot find this Skylark module");
      return;
    }
    if (!(skylarkRef instanceof BuildFile)) {
      markError(children[0], children[0].getText() + " is not a Skylark module");
      return;
    }

    LoadedSymbol[] symbols =
        Arrays.stream(children)
            .filter(element -> element instanceof LoadedSymbol)
            .toArray(LoadedSymbol[]::new);
    if (symbols.length == 1) {
      markError(node, "No symbols imported from Skylark module");
      return;
    }
    BuildFile skylarkModule = (BuildFile) skylarkRef;
    for (int i = 0; i < symbols.length; i++) {
      String text = symbols[i].getSymbolString();
      if (text == null) {
        continue;
      }
      FunctionStatement fn = skylarkModule.findDeclaredFunction(text);
      if (fn == null) {
        markError(
            symbols[i],
            "Function '" + text + "' not found in Skylark module " + skylarkModule.getFileName());
      }
    }
  }

  @Nullable
  private static PsiElement getSkylarkRef(BuildElement firstChild) {
    if (firstChild instanceof StringLiteral) {
      return new LabelReference((StringLiteral) firstChild, false).resolve();
    }
    return null;
  }

  @Override
  public void visitFuncallExpression(FuncallExpression node) {
    FunctionStatement function = (FunctionStatement) node.getReferencedElement();
    if (function == null) {
      // likely a built-in rule.
      return;
    }
    // check keyword args match function parameters
    ParameterList params = function.getParameterList();
    if (params == null || params.hasStarStar()) {
      return;
    }
    for (Argument arg : node.getArguments()) {
      if (arg instanceof Argument.Keyword) {
        String name = arg.getName();
        if (name != null && params.findParameterByName(name) == null) {
          markError(
              arg,
              "No parameter found in '" + node.getFunctionName() + "' with name '" + name + "'");
        }
      }
    }
  }
}
