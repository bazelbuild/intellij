package com.google.idea.blaze.base.lang.buildfile.completion;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.ExternalWorkspaceFixture;
import com.google.idea.blaze.base.lang.buildfile.BuildFileIntegrationTestCase;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.model.ExternalWorkspaceData;
import com.google.idea.blaze.base.model.primitives.ExternalWorkspace;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupEx;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import org.junit.Ignore;
import org.junit.Test;

import static com.google.common.truth.ExpectFailure.expectFailure;
import static com.google.common.truth.Truth.assertThat;

public class ExternalWorkspaceCompletionTest extends BuildFileIntegrationTestCase {
  protected ExternalWorkspaceFixture workspaceOne;
  protected ExternalWorkspaceFixture workspaceTwoMapped;

  @Override
  protected ExternalWorkspaceData mockExternalWorkspaceData() {
    workspaceOne =
        createExternalWorkspaceFixture(ExternalWorkspace.create("workspace_one", "workspace_one"));

    workspaceTwoMapped =
        createExternalWorkspaceFixture(
            ExternalWorkspace.create("workspace_two", "com_workspace_two"));

    return ExternalWorkspaceData.create(
        ImmutableList.of(workspaceOne.workspace, workspaceTwoMapped.workspace));
  }

  @Test
  public void testEmptyLabelCompletion() throws Throwable {
    PsiFile file = testFixture.configureByText("BUILD", "'<caret>'");

    String[] strings = editorTest.getCompletionItemsAsStrings();
    assertThat(strings).hasLength(2);
    assertThat(strings).asList().containsExactly("@com_workspace_two", "@workspace_one");
  }

  @Test
  public void testCompleteWillIncludeSlashes() {
    PsiFile file = testFixture.configureByText("BUILD", "'@com<caret>'");
    assertThat(editorTest.completeIfUnique()).isTrue();

    assertFileContents(file, "'@com_workspace_two//'");
  }

  @Test
  public void testCompleteWillFixUpRemainingSlashed() {
    PsiFile file = testFixture.configureByText("BUILD", "'@com<caret>//'");
    assertThat(editorTest.completeIfUnique()).isTrue();

    assertFileContents(file, "'@com_workspace_two//'");
  }

  @Test
  public void testCompleteWillAlwaysReplaceWorkspace() {
    PsiFile file = testFixture.configureByText("BUILD", "'@com<caret>xxxx//'");
    assertThat(editorTest.completeIfUnique()).isTrue();

    assertFileContents(file, "'@com_workspace_two//'");
  }

  @Test
  public void testCompleteWillRespectAutoQuoting() {
    boolean old = CodeInsightSettings.getInstance().AUTOINSERT_PAIR_QUOTE;
    PsiFile file;
    try {
      CodeInsightSettings.getInstance().AUTOINSERT_PAIR_QUOTE = false;
      file = testFixture.configureByText("BUILD", "'@com<caret>");

      assertThat(editorTest.completeIfUnique()).isTrue();
      assertFileContents(file, "'@com_workspace_two//");

      CodeInsightSettings.getInstance().AUTOINSERT_PAIR_QUOTE = true;
      file = testFixture.configureByText("BUILD", "'@com<caret>");

      assertThat(editorTest.completeIfUnique()).isTrue();
      assertFileContents(file, "'@com_workspace_two//'");

    } finally {
      CodeInsightSettings.getInstance().AUTOINSERT_PAIR_QUOTE = old;
    }
  }

  @Test
  public void testSlashWillAutoCompleteCurrentItem() throws Throwable {
    PsiFile file = testFixture.configureByText("BUILD", "'@<caret>comxxxx//'");

    assertThat(testFixture.completeBasic()).isNotNull();
    LookupEx lookup = testFixture.getLookup();

    LookupElement currentItem = lookup.getCurrentItem();
    assertThat(currentItem).isNotNull();

    testFixture.type('/');
    assertFileContents(file, String.format("'%s//'", currentItem.getLookupString()));
  }
}
