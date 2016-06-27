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

import com.google.idea.blaze.base.lang.buildfile.psi.*;
import com.google.idea.blaze.base.lang.buildfile.references.LabelReference;
import com.intellij.psi.PsiElement;

/**
 * Additional error annotations, post parsing.<p>
 * This has been turned off because it's unusable. BuildFile is re-parsed *every* time it's touched, and is never
 * cached. Until this is fixed, we can't run any annotators touching the file.<p>
 *
 * One option: try moving all expensive checks to 'visitFile', so they're not run in parallel
 */
public class ErrorAnnotator extends BuildAnnotator {

  @Override
  public void visitLoadStatement(LoadStatement node) {
    StringLiteral[] strings = node.getChildStrings();
    if (strings.length == 0) {
      return;
    }
    PsiElement skylarkRef = new LabelReference(strings[0], false).resolve();
    if (skylarkRef == null) {
      markError(strings[0], "Cannot find this Skylark module");
      return;
    }
    if (!(skylarkRef instanceof BuildFile)) {
      markError(strings[0], strings[0].getText() + " is not a Skylark module");
      return;
    }
    if (strings.length == 1) {
      markError(node, "No definitions imported from Skylark module");
      return;
    }
    BuildFile skylarkModule = (BuildFile) skylarkRef;
    for (int i = 1; i < strings.length; i++) {
      String text = strings[i].getStringContents();
      FunctionStatement fn = skylarkModule.findDeclaredFunction(text);
      if (fn == null) {
        markError(strings[i], "Function '" + text + "' not found in Skylark module " + skylarkModule.getFileName());
      }
    }
  }

  @Override
  public void visitFuncallExpression(FuncallExpression node) {
    FunctionStatement function = (FunctionStatement) node.getReferencedElement();
    if (function == null) {
      // likely a built-in rule. We don't yet recognize these.
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
          markError(arg, "No parameter found in '" + node.getFunctionName() + "' with name '" + name + "'");
        }
      }
    }
  }
}
