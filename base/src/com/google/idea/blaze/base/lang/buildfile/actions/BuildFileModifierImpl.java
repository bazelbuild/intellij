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
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.actions.BuildFileUtils;
import com.google.idea.blaze.base.buildmodifier.BuildFileModifier;
import com.google.idea.blaze.base.lang.buildfile.psi.*;
import com.google.idea.blaze.base.lang.buildfile.psi.util.BuildElementGenerator;
import com.google.idea.blaze.base.lang.buildfile.psi.util.PsiUtils;
import com.google.idea.blaze.base.lang.buildfile.references.BuildReferenceManager;
import com.google.idea.blaze.base.lang.buildfile.search.BlazePackage;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;
import java.util.stream.Stream;

/** Implementation of BuildFileModifier. Modifies the PSI tree directly. */
public class BuildFileModifierImpl implements BuildFileModifier {

  private static final Logger logger = Logger.getInstance(BuildFileModifierImpl.class);

  @Override
  public boolean addRule(Project project, Label newRule, Kind ruleKind) {
    BuildReferenceManager manager = BuildReferenceManager.getInstance(project);
    File file = manager.resolvePackage(newRule.blazePackage());
    if (file == null) {
      return false;
    }
    LocalFileSystem.getInstance().refreshIoFiles(ImmutableList.of(file));
    BuildFile buildFile = manager.resolveBlazePackage(newRule.blazePackage());
    if (buildFile == null) {
      logger.error("No BUILD file found at location: " + newRule.blazePackage());
      return false;
    }
    buildFile.add(createRule(project, ruleKind, newRule.targetName().toString()));
    return true;
  }

  @Override
  public boolean addDepToRule(Project project,
                              Label targetToBeAdded,
                              VirtualFile virtualFileToBeEdited) {
    boolean wasDepsAddedToRule = false;

    Optional<BlazePackage> parentPackage = Optional.of(BuildFileUtils.getBuildFile(project, virtualFileToBeEdited));
    if (parentPackage.isPresent()) {
        Optional<PsiElement> targetToBeEdited =
              Optional.fromNullable(
                      BuildFileUtils.findBuildTarget(
                              project,
                              parentPackage.get(),
                              new File(virtualFileToBeEdited.getPath())
                      )
              );
        if(targetToBeEdited.isPresent()){
            java.util.Optional<Argument.Keyword> maybeDepsKeyword = findTargetDepsAttribute(targetToBeEdited.get());
            if(!maybeDepsKeyword.isPresent()){
                ArgumentList targetAttributes = PsiUtils.findFirstChildOfClassRecursive(targetToBeEdited.get(), ArgumentList.class);
                addCommaToTargetAttributesIfNeeded(project, targetAttributes, ",\n\t");
                addDepsAttributeToTargetAttributes(targetAttributes, project);
                maybeDepsKeyword = findTargetDepsAttribute(targetToBeEdited.get());
            }
            addTargetToDepsIfNeeded(maybeDepsKeyword.get(), targetToBeAdded, project);
            wasDepsAddedToRule = true;
        }
    }

    return wasDepsAddedToRule;
  }

    @NotNull
    private java.util.Optional<Argument.Keyword> findTargetDepsAttribute(PsiElement psiElement) {
        List<Argument.Keyword> allChildKeywords = PsiUtils.findAllChildrenOfClassRecursive(psiElement, Argument.Keyword.class);
        Stream<Argument.Keyword> depsKeywords = allChildKeywords.stream().filter(keyword -> keyword.getName().equals("deps"));
        return depsKeywords.findFirst();
    }

    private void addTargetToDepsIfNeeded(Argument.Keyword depsKeyword, Label targetToBeAdded, Project project) {
        PsiElement values = depsKeyword.getPsiChild(BuildElementTypes.LIST_LITERAL, null);
        if(!doesDepsContainTarget(targetToBeAdded, values)){
            addTargetToDeps(targetToBeAdded, project, values);
        }
    }

    private void addTargetToDeps(Label targetToBeAdded, Project project, PsiElement values) {
        PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
        addCommaToTargetAttributesIfNeeded(project, values, ", ");
        addTargetToDeps(targetToBeAdded, values, factory);
    }

    private boolean doesDepsContainTarget(Label targetToBeAdded, PsiElement values) {
        return values.getText().contains(targetToBeAdded.toString());
    }

    private void addDepsAttributeToTargetAttributes(PsiElement values, Project project) {
        Argument.Keyword deps = BuildElementGenerator.getInstance(project).createKeywordArgumentWithListValue("deps");
        values.addBefore(deps, values.getLastChild());
    }

    private void addTargetToDeps(Label targetToBeAdded, PsiElement values, PsiElementFactory factory) {
        PsiLiteralExpression stringLiteral = (PsiLiteralExpression)factory.createExpressionFromText("\""+targetToBeAdded.toString()+"\"", null);
        values.addBefore(stringLiteral, values.getLastChild());
    }

    private void addCommaToTargetAttributesIfNeeded(Project project, PsiElement attributes, String whiteSpaceValue) {
      if(attributes.getChildren().length > 0){
          PsiElement commaLiteral = PsiParserFacade.SERVICE.getInstance(project).createWhiteSpaceFromText(whiteSpaceValue);
          attributes.addBefore(commaLiteral, attributes.getLastChild());
      }
    }

    private PsiElement createRule(Project project, Kind ruleKind, String ruleName) {
    String text =
        Joiner.on("\n").join(ruleKind.toString() + "(", "    name = \"" + ruleName + "\"", ")");
    Expression expr = BuildElementGenerator.getInstance(project).createExpressionFromText(text);
    assert (expr instanceof FuncallExpression);
    return expr;
  }
}
