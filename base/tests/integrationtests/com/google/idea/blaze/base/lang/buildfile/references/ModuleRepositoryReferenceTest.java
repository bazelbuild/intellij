package com.google.idea.blaze.base.lang.buildfile.references;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.ExternalWorkspaceFixture;
import com.google.idea.blaze.base.lang.buildfile.BuildFileIntegrationTestCase;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.model.ExternalWorkspaceData;
import com.google.idea.blaze.base.model.primitives.ExternalWorkspace;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertNotNull;

@RunWith(JUnit4.class)
public class ModuleRepositoryReferenceTest extends BuildFileIntegrationTestCase {

  protected ExternalWorkspaceFixture unmappedWorkspace;
  protected ExternalWorkspaceFixture remappedWorkspace;

  @Override
  protected ExternalWorkspaceData mockExternalWorkspaceData() {
    System.out.println("mockExternalWorkspaceData called");
    unmappedWorkspace = new ExternalWorkspaceFixture(
        ExternalWorkspace.create("workspace_one", "workspace_one"), fileSystem);

    remappedWorkspace = new ExternalWorkspaceFixture(
        ExternalWorkspace.create("workspace_two", "com_workspace_two"), fileSystem);

    return ExternalWorkspaceData.create(
        ImmutableList.of(unmappedWorkspace.workspace, remappedWorkspace.workspace));
  }

  @Test
  public void testUnmappedExternalWorkspaceCompletion() throws Throwable {
    WorkspaceRoot externalWorkspaceRoot = unmappedWorkspace.getWorkspaceRoot();
    assertNotNull(externalWorkspaceRoot);

    BuildFile otherPackage =
        unmappedWorkspace.createBuildFile(
            new WorkspacePath( "p1/p2/BUILD"),
            "java_library(name = 'rule1')");

    String targetRule = "@" + unmappedWorkspace.workspace.repoName() + "//p1/p2:rule1";

    BuildFile file = createBuildFile(
        new WorkspacePath("java/BUILD"),
        "java_library(",
        "    name = 'lib',",
        "    deps = ['" + targetRule + "']");

    Editor editor = editorTest.openFileInEditor(file);
    editorTest.setCaretPosition(editor, 2, ("    deps = ['" + targetRule).length());

    PsiElement target =
        GotoDeclarationAction.findTargetElement(
            getProject(), editor, editor.getCaretModel().getOffset());

    assertThat(target).isNotNull();
  }

  @Test
  public void testRemappedExternalWorkspaceCompletion() throws Throwable {
    WorkspaceRoot externalWorkspaceRoot = remappedWorkspace.getWorkspaceRoot();
    assertNotNull(externalWorkspaceRoot);

    BuildFile otherPackage =
        remappedWorkspace.createBuildFile(
            new WorkspacePath("p1/p2/BUILD"),
            "java_library(name = 'rule1')");

    String targetRule = "@" + remappedWorkspace.workspace.repoName() + "//p1/p2:rule1";

    BuildFile file = createBuildFile(
        new WorkspacePath("java/BUILD"),
        "java_library(",
        "    name = 'lib',",
        "    deps = ['" + targetRule + "']");

    Editor editor = editorTest.openFileInEditor(file);
    editorTest.setCaretPosition(editor, 2, ("    deps = ['" + targetRule).length());

    PsiElement target =
        GotoDeclarationAction.findTargetElement(
            getProject(), editor, editor.getCaretModel().getOffset());

    assertThat(target).isNotNull();
  }
}
