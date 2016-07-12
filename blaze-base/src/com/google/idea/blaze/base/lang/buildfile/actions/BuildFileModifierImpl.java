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
package com.google.idea.blaze.base.lang.buildfile.actions;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.buildmodifier.BuildFileModifier;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.lang.buildfile.psi.Expression;
import com.google.idea.blaze.base.lang.buildfile.psi.FuncallExpression;
import com.google.idea.blaze.base.lang.buildfile.psi.util.BuildElementGenerator;
import com.google.idea.blaze.base.lang.buildfile.references.BuildReferenceManager;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.psi.PsiElement;

import java.io.File;

/**
 * Implementation of BuildFileModifier. Modifies the PSI tree directly.
 */
public class BuildFileModifierImpl implements BuildFileModifier {

  private static final Logger LOG = Logger.getInstance(BuildFileModifierImpl.class);

  @Override
  public boolean addRule(Project project,
                         BlazeContext context,
                         Label newRule,
                         Kind ruleKind) {
    return WriteCommandAction.runWriteCommandAction(project, (Computable<Boolean>) () -> {
      BuildReferenceManager manager = BuildReferenceManager.getInstance(project);
      File file = manager.resolvePackage(newRule.blazePackage());
      if (file == null) {
        return null;
      }
      LocalFileSystem.getInstance().refreshIoFiles(ImmutableList.of(file));
      BuildFile buildFile = manager.resolveBlazePackage(newRule.blazePackage());
      if (buildFile == null) {
        LOG.error("No BUILD file found at location: " + newRule.blazePackage());
        return false;
      }
      buildFile.add(createRule(project, ruleKind, newRule.ruleName().toString()));
      return true;
    });
  }

  private PsiElement createRule(Project project, Kind ruleKind, String ruleName) {
    String text = Joiner.on("\n").join(
      ruleKind.toString() + "(",
      "    name = \"" + ruleName + "\"",
      ")"
    );
    Expression expr = BuildElementGenerator.getInstance(project).createExpressionFromText(text);
    assert(expr instanceof FuncallExpression);
    return expr;
  }

}
