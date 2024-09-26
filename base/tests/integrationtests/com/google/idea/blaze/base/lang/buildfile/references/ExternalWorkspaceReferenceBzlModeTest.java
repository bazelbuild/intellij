package com.google.idea.blaze.base.lang.buildfile.references;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.ExternalWorkspaceFixture;
import com.google.idea.blaze.base.lang.buildfile.BuildFileIntegrationTestCase;
import com.google.idea.blaze.base.model.ExternalWorkspaceData;
import com.google.idea.blaze.base.model.primitives.ExternalWorkspace;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.TargetName;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static com.google.common.truth.Truth.assertThat;

@RunWith(JUnit4.class)
public class ExternalWorkspaceReferenceBzlModeTest extends BuildFileIntegrationTestCase {

  protected ExternalWorkspaceFixture workspaceOne;
  protected ExternalWorkspaceFixture workspaceTwo;

  @Override
  protected ExternalWorkspaceData mockExternalWorkspaceData() {
    workspaceOne = createExternalWorkspaceFixture(
        ExternalWorkspace.create("workspace_one", "workspace_one"));

    workspaceTwo = createExternalWorkspaceFixture(
        ExternalWorkspace.create("workspace_two", "com_workspace_two"));

    return ExternalWorkspaceData.create(
        ImmutableList.of(workspaceOne.workspace, workspaceTwo.workspace));
  }

  @Before
  public void doSetupExternalWorkspaces() {
    workspaceOne.createBuildFile(
        new WorkspacePath("p1/p2/BUILD"),
        "java_library(name = 'rule1')");

    workspaceTwo.createBuildFile(
        new WorkspacePath("p1/p2/BUILD"),
        "java_library(name = 'rule1')");
  }

  @Test
  public void testUnmappedExternalWorkspace() throws Throwable {
    Label targetLabel = workspaceOne.createLabel(
        new WorkspacePath("p1/p2"), TargetName.create("rule1"));

    PsiFile file = testFixture.configureByText("BUILD",
        """
            java_library(
                name = 'lib',
                deps = ['@workspace_one//p1/p2:rule1<caret>']
            )""");

    Editor editor = testFixture.getEditor();
    PsiElement target =
        GotoDeclarationAction.findTargetElement(
            getProject(), editor, editor.getCaretModel().getOffset());
    assertThat(target).isNotNull();
  }

  @Test
  public void testRemappedExternalWorkspace() throws Throwable {
    Label targetLabel = workspaceTwo.createLabel(
        new WorkspacePath("p1/p2"), TargetName.create("rule1"));

    testFixture.configureByText("BUILD",
        """
            java_library(
                name = 'lib',
                deps = ['@com_workspace_two//p1/p2:rule1<caret>']
            )""");

    Editor editor = testFixture.getEditor();
    PsiElement target =
        GotoDeclarationAction.findTargetElement(
            getProject(), editor, editor.getCaretModel().getOffset());

    assertThat(target).isNotNull();
  }
}
