package com.google.idea.blaze.base.lang.buildfile.completion;

import com.google.idea.blaze.base.lang.buildfile.BuildFileIntegrationTestCase;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.lang.buildfile.psi.FuncallExpression;
import com.google.idea.blaze.base.lang.buildfile.psi.FunctionStatement;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.intellij.openapi.editor.Editor;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static com.google.common.truth.Truth.assertThat;

@RunWith(JUnit4.class)
public class PreludeBasedCompletionTest extends BuildFileIntegrationTestCase {

  private BuildFile buildDefs;

  @Before
  public void setup() {
    buildDefs = createBuildFile(new WorkspacePath("java/com/google/build_defs.bzl"),
        "def function(name, deps)\n def test_function(arg)");
    createBuildFile(new WorkspacePath("java/com/google/build_rules.bzl"), "test_rule = rule()");
    workspace.createFile(new WorkspacePath("tools/build_rules/prelude_bazel"),
        "load(",
        "\"//java/com/google:build_defs.bzl\",",
        "\"function\"",
        ")",
        "load(",
        "\"//java/com/google:build_rules.bzl\",",
        "\"test_rule\"",
        ")");
  }


  @Test
  public void testLoadedFunctionsCompletion() {
    BuildFile file = createBuildFile(new WorkspacePath("BUILD"), "funct");

    Editor editor = editorTest.openFileInEditor(file.getVirtualFile());
    editorTest.setCaretPosition(editor, 0, 4);

    String[] completionItems = editorTest.getCompletionItemsAsStrings();
    assertThat(completionItems).asList().containsExactly("function");
  }

  @Test
  public void testLoadedRuleCompletion() {
    BuildFile file = createBuildFile(new WorkspacePath("BUILD"), "test_");

    Editor editor = editorTest.openFileInEditor(file.getVirtualFile());
    editorTest.setCaretPosition(editor, 0, 4);

    String[] completionItems = editorTest.getCompletionItemsAsStrings();
    assertThat(completionItems).asList().containsExactly("test_rule");
  }

  @Test
  public void testFunctionCompletionWhenFunctionIsNotLoaded() {
    BuildFile file = createBuildFile(new WorkspacePath("BUILD"), "test_func");

    Editor editor = editorTest.openFileInEditor(file.getVirtualFile());
    editorTest.setCaretPosition(editor, 0, 9);

    String[] completionItems = editorTest.getCompletionItemsAsStrings();
    assertThat(completionItems).asList().isEmpty();
  }

  @Test
  public void testReferenceToLoadedFunction() {
    BuildFile file = createBuildFile(new WorkspacePath("BUILD"), "function()");

    FunctionStatement function = buildDefs.firstChildOfClass(FunctionStatement.class);
    FuncallExpression funcall = file.firstChildOfClass(FuncallExpression.class);

    assertThat(funcall.getReferencedElement()).isEqualTo(function);
  }

  @Override
  protected boolean isLightTestCase() {
    return false;
  }

}
