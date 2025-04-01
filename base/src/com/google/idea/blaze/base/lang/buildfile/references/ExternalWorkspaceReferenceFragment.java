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
package com.google.idea.blaze.base.lang.buildfile.references;

import com.google.idea.blaze.base.lang.buildfile.completion.BuildLookupElement;
import com.google.idea.blaze.base.lang.buildfile.completion.ExternalWorkspaceLookupElement;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildElement;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.lang.buildfile.psi.FuncallExpression;
import com.google.idea.blaze.base.lang.buildfile.psi.StringLiteral;
import com.google.idea.blaze.base.lang.buildfile.psi.util.PsiUtils;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.workspace.WorkspaceHelper;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** The external workspace component of a label (between '@' and '//') */
public class ExternalWorkspaceReferenceFragment extends PsiReferenceBase<StringLiteral> {

  public ExternalWorkspaceReferenceFragment(LabelReference labelReference) {
    super(labelReference.getElement(), labelReference.getRangeInElement(), labelReference.isSoft());
  }

  @Override
  public TextRange getRangeInElement() {
    String rawText = myElement.getText();
    String unquotedText = LabelUtils.trimToDummyIdentifier(myElement.getStringContents());
    QuoteType quoteType = myElement.getQuoteType();

    String externalWorkspace = LabelUtils.getExternalWorkspaceComponent(unquotedText);
    if (!unquotedText.trim().isEmpty() && externalWorkspace == null) {
      return TextRange.EMPTY_RANGE;
    }

    int endIndex = rawText.indexOf("//");
    if (endIndex == -1) {
      endIndex = rawText.length() - quoteType.quoteString.length();
    } else {
      endIndex += 2;
    }
    return new TextRange(1, endIndex);
  }

  @Nullable
  @Override
  public BuildElement resolve() {
    String name = LabelUtils.getExternalWorkspaceComponent(myElement.getStringContents());
    if (name == null) {
      return null;
    }

    BuildFile workspaceFile = resolveProjectWorkspaceFile(myElement.getProject());
    if (workspaceFile != null) {
      FuncallExpression expression = workspaceFile.findRule(name);
      if (expression != null) {
        return expression;
      }
    };

    WorkspaceRoot workspaceRoot = WorkspaceHelper.getExternalWorkspace(myElement.getProject(), name);
    if (workspaceRoot != null) {
      return BuildReferenceManager.getInstance(myElement.getProject()).findBuildFile(workspaceRoot.directory());
    }

    return null;
  }

  @Nullable
  private static BuildFile resolveProjectWorkspaceFile(Project project) {
    WorkspaceRoot projectRoot = WorkspaceRoot.fromProjectSafe(project);
    if (projectRoot == null) {
      return null;
    }
    for (String workspaceFileName :
        Blaze.getBuildSystemProvider(project).possibleWorkspaceFileNames()) {
      PsiFileSystemItem workspaceFile =
          BuildReferenceManager.getInstance(project)
              .resolveFile(projectRoot.fileForPath(new WorkspacePath(workspaceFileName)));
      if (workspaceFile != null) {
        return ObjectUtils.tryCast(workspaceFile, BuildFile.class);
      }
    }
    return null;
  }

  @Override
  @Nonnull
  public BuildLookupElement[] getVariants() {
    BlazeProjectData blazeProjectData = BlazeProjectDataManager.getInstance(myElement.getProject()).getBlazeProjectData();
    if (blazeProjectData == null) {
      return BuildLookupElement.EMPTY_ARRAY;
    }

    return blazeProjectData.getExternalWorkspaceData().workspaces.values().stream()
        .map(ExternalWorkspaceLookupElement::new)
        .toArray(BuildLookupElement[]::new);
  }

  @Override
  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    String oldString = myElement.getStringContents();
    int slashesIndex = oldString.indexOf("//");
    String newString =
        String.format(
            "@%s%s", newElementName, slashesIndex == -1 ? "" : oldString.substring(slashesIndex));
    return handleRename(newString);
  }

  @Override
  public PsiElement bindToElement(PsiElement element) throws IncorrectOperationException {
    return myElement;
  }

  private PsiElement handleRename(String newStringContents) {
    ASTNode node = myElement.getNode();
    node.replaceChild(
        node.getFirstChildNode(),
        PsiUtils.createNewLabel(myElement.getProject(), newStringContents));
    return myElement;
  }
}
