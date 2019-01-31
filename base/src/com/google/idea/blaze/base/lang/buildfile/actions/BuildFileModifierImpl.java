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
import com.intellij.execution.OutputListener;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.lang.ASTFactory;
import com.intellij.lang.Language;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.GeneratedMarkerVisitor;
import com.intellij.psi.impl.source.DummyHolderFactory;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.tree.IElementType;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/** Implementation of BuildFileModifier. Modifies the PSI tree directly. */
public class BuildFileModifierImpl implements BuildFileModifier {

    private static final Logger logger = Logger.getInstance(BuildFileModifierImpl.class);
    public static final String ATTRIBUTE_NAME = "name";
    public static final String ATTRIBUTE_DEPS = "deps";
    public static final String BUILDOZER_COMMAND = "buildozer";
    public static final String ADD_DEPS_COMMAND = "add deps ";

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
            VirtualFile workDirectory = findWorkspaceFile(project.getWorkspaceFile());

            if(isBuildozerInstalled(workDirectory) && workDirectory != null){
                addDependencyUsingBuildozer(workDirectory, targetToBeAdded, targetToBeEdited);
            } else {
                addUsingBuildFileUtils(project, targetToBeAdded, targetToBeEdited);
            }

            wasDepsAddedToRule = true;
        }
    }

    return wasDepsAddedToRule;
  }

  private void addDependencyUsingBuildozer(VirtualFile workDirectory, Label targetToBeAdded, Optional<PsiElement> targetToBeEdited) {
      try {
          java.util.Optional<Argument.Keyword> targetNameAttribute = findTargetAttribute(targetToBeEdited.get(), ATTRIBUTE_NAME);
          PsiElement targetToBeEditedPsiElement = targetNameAttribute.get().getPsiChild(BuildElementTypes.STRING_LITERAL, null);
          String targetToBeEditedDirectory = getTargetDirectory(targetToBeEdited);
          String targetToBeEditedName = targetToBeEditedPsiElement.getText().replaceAll("\"", "");
          String targetToBeEditedFullPath = targetToBeEditedDirectory+":"+targetToBeEditedName;
          List<String> commandList = new ArrayList<>();
          commandList.add(BUILDOZER_COMMAND);
          commandList.add(ADD_DEPS_COMMAND +targetToBeAdded.toString());
          commandList.add(targetToBeEditedFullPath);

          runCliCommand(
                  commandList,
                  workDirectory.getPath()
          );
      } catch (Exception e) {
          logger.error("failed adding using buildozer ", e);

      }
  }

    @NotNull
    private String getTargetDirectory(Optional<PsiElement> targetToBeEdited) {
        return targetToBeEdited.get()
                .getContainingFile()
                .toString()
                .replace("/BUILD.bazel", "")
                .replace("/BUILD", "");
    }

    private boolean isBuildozerInstalled(VirtualFile workDirectory) {
        try {
            List<String> command = Arrays.asList("which buildozer".split(" "));
            String buildozerInstallationPath = runCliCommand(command, workDirectory.getPath());
            return StringUtils.isNoneEmpty(buildozerInstallationPath);
        } catch (Exception e) {
            logger.error("failed checking for buildozer installation falling back to PSIElement manipulation ", e);
            return false;
        }
    }

    private String runCliCommand(List<String> command, String path) throws Exception {
        GeneralCommandLine generalCommandLine = new GeneralCommandLine(command);

        generalCommandLine.setWorkDirectory(path);
        OSProcessHandler osProcessHandler = new OSProcessHandler(generalCommandLine.createProcess(), generalCommandLine.getCommandLineString());
        OutputListener outputListener = new OutputListener();
        osProcessHandler.addProcessListener(outputListener);
        osProcessHandler.startNotify();
        osProcessHandler.waitFor(1000);
        if(outputListener.getOutput().getExitCode() != 0){
            throw new Exception("Failed buildozer invocation: "+outputListener.getOutput().getStderr());
        }

        return outputListener.getOutput().getStdout();
    }

    @Nullable
    private VirtualFile findWorkspaceFile(VirtualFile folder) {
        while (folder != null) {
            java.util.Optional<VirtualFile> workspaceFile = Arrays.asList(folder.getChildren())
                    .stream()
                    .filter(file -> isWorkspaceFile(file))
                    .findFirst();
            if (workspaceFile.isPresent()) {
                return folder;
            }

            folder = folder.getParent();
        }
        return null;
    }

    private boolean isWorkspaceFile(VirtualFile file) {
        return file.getName().equals("WORKSPACE");
    }

    private void addUsingBuildFileUtils(Project project, Label targetToBeAdded, Optional<PsiElement> targetToBeEdited) {
        java.util.Optional<Argument.Keyword> maybeDepsKeyword = findTargetAttribute(targetToBeEdited.get(), "deps");
        if(!maybeDepsKeyword.isPresent()){
            maybeDepsKeyword = addDepsAttribute(project, targetToBeEdited.get());
        }
        addTargetToDepsIfNeeded(maybeDepsKeyword.get(), targetToBeAdded, project);
    }

    @NotNull
    private java.util.Optional<Argument.Keyword> addDepsAttribute(Project project, PsiElement targetToBeEdited) {
        java.util.Optional<Argument.Keyword> maybeDepsKeyword;
        ArgumentList targetAttributes = PsiUtils.findFirstChildOfClassRecursive(targetToBeEdited, ArgumentList.class);
        addDepsAttributeToTargetAttributes(targetAttributes, project);
        maybeDepsKeyword = findTargetAttribute(targetToBeEdited, "deps");
        PsiElement whiteSpaceLiteral = PsiParserFacade.SERVICE.getInstance(project).createWhiteSpaceFromText("\n    ");
        targetAttributes.addBefore(whiteSpaceLiteral, maybeDepsKeyword.get());
        PsiElement commaLiteral = createCommaElement(project);
        targetAttributes.addAfter(commaLiteral, maybeDepsKeyword.get());

        return maybeDepsKeyword;
    }

    @NotNull
    private java.util.Optional<Argument.Keyword> findTargetAttribute(PsiElement psiElement, String attributeName) {
        List<Argument.Keyword> allChildKeywords = PsiUtils.findAllChildrenOfClassRecursive(psiElement, Argument.Keyword.class);
        Stream<Argument.Keyword> depsKeywords = allChildKeywords.stream().filter(keyword -> keyword.getName().equals(attributeName));
        return depsKeywords.findFirst();
    }

    private void addTargetToDepsIfNeeded(Argument.Keyword depsKeyword, Label targetToBeAdded, Project project) {
        PsiElement depsElement = depsKeyword.getPsiChild(BuildElementTypes.LIST_LITERAL, null);
        if(depsDoesntHaveWhiteSpace(depsElement)){
            addWhiteSpaceElement(project, depsElement, "\n    ");
        }
        if(!doesDepsContainTarget(targetToBeAdded, depsElement)){
            addTargetToDeps(targetToBeAdded, project, depsElement);
        }
    }

    private boolean depsDoesntHaveWhiteSpace(PsiElement depsElement) {
        PsiWhiteSpace lastWhiteSpace = PsiUtils.findLastChildOfClassRecursive(depsElement, PsiWhiteSpace.class);
        return lastWhiteSpace == null;
    }

    private void addTargetToDeps(Label targetToBeAdded, Project project, PsiElement depsElement) {
        PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
        PsiLiteralExpression stringLiteral = (PsiLiteralExpression)factory.createExpressionFromText("\""+targetToBeAdded.toString()+"\"", null);
        PsiWhiteSpace lastWhiteSpace = PsiUtils.findLastChildOfClassRecursive(depsElement, PsiWhiteSpace.class);
        depsElement.addBefore(stringLiteral, lastWhiteSpace);

        PsiElement commaLiteral = createCommaElement(project);
        lastWhiteSpace = PsiUtils.findLastChildOfClassRecursive(depsElement, PsiWhiteSpace.class);
        depsElement.addBefore(commaLiteral,lastWhiteSpace);

        PsiLiteralExpression addedTarget = PsiUtils.findLastChildOfClassRecursive(depsElement, PsiLiteralExpression.class);
        PsiElement whiteSpaceLiteral = PsiParserFacade.SERVICE.getInstance(project).createWhiteSpaceFromText("\n        ");
        depsElement.addBefore(whiteSpaceLiteral, addedTarget);
    }

    private boolean doesDepsContainTarget(Label targetToBeAdded, PsiElement values) {
        return values.getText().contains(targetToBeAdded.toString());
    }

    private void addDepsAttributeToTargetAttributes(PsiElement depsElement, Project project) {
        Argument.Keyword deps = BuildElementGenerator.getInstance(project).createKeywordArgumentWithListValue(ATTRIBUTE_DEPS);
        depsElement.addBefore(deps, depsElement.getLastChild().getPrevSibling());
    }

    private void addWhiteSpaceElement(Project project, PsiElement element, String whiteSpaceValue) {
        PsiElement whiteSpaceLiteral = PsiParserFacade.SERVICE.getInstance(project).createWhiteSpaceFromText(whiteSpaceValue);
        element.addBefore(whiteSpaceLiteral, element.getLastChild());
    }

    private PsiElement createCommaElement(Project project) {
        PsiManager psiManager = PsiManager.getInstance(project);
        final FileElement holderElement = DummyHolderFactory.createHolder(psiManager, null).getTreeElement();
        final LeafElement newElement = ASTFactory.leaf(new IElementType("COMMA", Language.ANY), holderElement.getCharTable().intern(","));
        holderElement.rawAddChildren(newElement);
        GeneratedMarkerVisitor.markGenerated(newElement.getPsi());
        return newElement.getPsi();
    }

    private PsiElement createRule(Project project, Kind ruleKind, String ruleName) {
    String text =
        Joiner.on("\n").join(ruleKind.toString() + "(", "    name = \"" + ruleName + "\"", ")");
    Expression expr = BuildElementGenerator.getInstance(project).createExpressionFromText(text);
    assert (expr instanceof FuncallExpression);
    return expr;
  }
}
