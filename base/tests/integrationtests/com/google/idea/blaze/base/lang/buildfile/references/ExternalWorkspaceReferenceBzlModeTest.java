package com.google.idea.blaze.base.lang.buildfile.references;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.ExternalWorkspaceFixture;
import com.google.idea.blaze.base.lang.buildfile.BuildFileIntegrationTestCase;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.model.ExternalWorkspaceData;
import com.google.idea.blaze.base.model.primitives.ExternalWorkspace;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.TargetName;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertNotNull;

@RunWith(JUnit4.class)
public class ExternalWorkspaceReferenceBzlModeTest extends BuildFileIntegrationTestCase {

  protected ExternalWorkspaceFixture workspaceOne;
  protected ExternalWorkspaceFixture workspaceTwoMapped;

  @Override
  protected ExternalWorkspaceData mockExternalWorkspaceData() {
    workspaceOne = new ExternalWorkspaceFixture(
        ExternalWorkspace.create("workspace_one", "workspace_one"), fileSystem);

    workspaceTwoMapped = new ExternalWorkspaceFixture(
        ExternalWorkspace.create("workspace_two", "com_workspace_two"), fileSystem);

    return ExternalWorkspaceData.create(
        ImmutableList.of(workspaceOne.w, workspaceTwoMapped.w));
  }

  @Before
  public void doSetupExternalWorkspaces() {
    workspaceOne.createBuildFile(
        new WorkspacePath("p1/p2/BUILD"),
        "java_library(name = 'rule1')");

    workspaceTwoMapped.createBuildFile(
        new WorkspacePath("p1/p2/BUILD"),
        "java_library(name = 'rule1')");
  }

  @Test
  public void testUnmappedExternalWorkspace() throws Throwable {
    Label targetLabel = workspaceOne.createLabel(new WorkspacePath("p1/p2"), TargetName.create("rule1"));

    BuildFile file = createBuildFile(
        new WorkspacePath("java/BUILD"),
        String.format("""
            java_library(
                name = 'lib',
                deps = ['%s']
            )""", targetLabel));

    Editor editor = editorTest.openFileInEditor(file);
    editorTest.setCaretPosition(editor, 2, ("    deps = ['" + targetLabel).length());

    PsiElement target =
        GotoDeclarationAction.findTargetElement(
            getProject(), editor, editor.getCaretModel().getOffset());

    assertThat(target).isNotNull();
  }

  @Test
  public void testRemappedExternalWorkspace() throws Throwable {
    Label targetLabel = workspaceTwoMapped.createLabel(new WorkspacePath("p1/p2"), TargetName.create("rule1"));

    BuildFile file = createBuildFile(
        new WorkspacePath("java/BUILD"),
        String.format("""
            java_library(
                name = 'lib',
                deps = ['%s']
            )""", targetLabel));

    Editor editor = editorTest.openFileInEditor(file);
    editorTest.setCaretPosition(editor, 2, ("    deps = ['" + targetLabel).length());

    PsiElement target =
        GotoDeclarationAction.findTargetElement(
            getProject(), editor, editor.getCaretModel().getOffset());

    assertThat(target).isNotNull();
  }
}
