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
package com.google.idea.blaze.base.lang.buildfile.parser;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.idea.blaze.base.lang.buildfile.BuildFileIntegrationTestCase;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildElement;
import com.google.idea.blaze.base.lang.buildfile.psi.LoadStatement;
import com.intellij.lang.ASTNode;
import com.intellij.lang.FileASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lang.impl.PsiBuilderAdapter;
import com.intellij.lang.impl.PsiBuilderImpl;
import com.intellij.lexer.Lexer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.CharTableImpl;
import com.intellij.psi.impl.source.tree.LeafElement;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/** Test for the BUILD file parser (converting lexical elements into PSI elements) */
public class BuildParserTest extends BuildFileIntegrationTestCase {

  private final List<String> errors = Lists.newArrayList();

  @Override
  protected void doTearDown() {
    errors.clear();
  }

  public void testAugmentedAssign() throws Exception {
    assertThat(parse("x += 1")).isEqualTo("aug_assign(reference, int)");
    assertThat(parse("x -= 1")).isEqualTo("aug_assign(reference, int)");
    assertThat(parse("x *= 1")).isEqualTo("aug_assign(reference, int)");
    assertThat(parse("x /= 1")).isEqualTo("aug_assign(reference, int)");
    assertThat(parse("x %= 1")).isEqualTo("aug_assign(reference, int)");
    assertNoErrors();
  }

  public void testAssign() throws Exception {
    assertThat(parse("a, b = 5\n")).isEqualTo("assignment(list(reference, target), int)");
    assertNoErrors();
  }

  public void testAssign2() throws Exception {
    assertThat(parse("a = b;c = d\n"))
        .isEqualTo(
            Joiner.on("").join("assignment(target, reference), ", "assignment(target, reference)"));
    assertNoErrors();
  }

  public void testInvalidAssign() throws Exception {
    parse("1 + (b = c)");
    assertContainsErrors();
  }

  public void testTupleAssign() throws Exception {
    assertThat(parse("list[0] = 5; dict['key'] = value\n"))
        .isEqualTo(
            Joiner.on("")
                .join(
                    "assignment(function_call(reference, positional(int)), int), ",
                    "assignment(function_call(reference, positional(string)), reference)"));
    assertNoErrors();
  }

  public void testPrimary() throws Exception {
    assertThat(parse("f(1 + 2)"))
        .isEqualTo("function_call(reference, arg_list(positional(binary_op(int, int))))");
    assertNoErrors();
  }

  public void testSecondary() throws Exception {
    assertThat(parse("f(1 % 2)"))
        .isEqualTo("function_call(reference, arg_list(positional(binary_op(int, int))))");
    assertNoErrors();
  }

  public void testDoesNotGetStuck() throws Exception {
    // Make sure the parser does not get stuck when trying
    // to parse an expression containing a syntax error.
    parse("f(1, ], 3)");
    parse("f(1, ), 3)");
    parse("[ ) for v in 3)");
    parse("f(1, [x for foo foo foo foo], 3)");
  }

  public void testInvalidFunctionStatementDoesNotGetStuck() throws Exception {
    // Make sure the parser does not get stuck when trying
    // to parse a function statement containing a syntax error.
    parse("def is ");
    parse("def fn(");
    parse("def empty)");
  }

  public void testSubstring() throws Exception {
    assertThat(parse("'FOO.CC'[:].lower()[1:]"))
        .isEqualTo(
            Joiner.on("")
                .join(
                    "function_call(",
                    "function_call(function_call(string), reference, arg_list), ",
                    "positional(int))"));
    assertNoErrors();
  }

  public void testFuncallExpr() throws Exception {
    assertThat(parse("foo(1, 2, bar=wiz)"))
        .isEqualTo(
            Joiner.on("")
                .join(
                    "function_call(reference, arg_list(",
                    "positional(int), ",
                    "positional(int), ",
                    "keyword(reference)))"));
    assertNoErrors();
  }

  public void testMethCallExpr() throws Exception {
    assertThat(parse("foo.foo(1, 2, bar=wiz)"))
        .isEqualTo(
            Joiner.on("")
                .join(
                    "function_call(reference, reference, ",
                    "arg_list(positional(int), positional(int), keyword(reference)))"));
    assertNoErrors();
  }

  public void testChainedMethCallExpr() throws Exception {
    assertThat(parse("foo.replace().split(1)"))
        .isEqualTo(
            "function_call(function_call(reference, reference, arg_list), "
                + "reference, arg_list(positional(int)))");
    assertNoErrors();
  }

  public void testPropRefExpr() throws Exception {
    assertThat(parse("foo.foo")).isEqualTo("dot_expr(reference, reference)");
    assertNoErrors();
  }

  public void testStringMethExpr() throws Exception {
    assertThat(parse("'foo'.foo()")).isEqualTo("function_call(string, reference, arg_list)");
    assertNoErrors();
  }

  public void testFuncallLocation() throws Exception {
    assertThat(parse("a(b);c = d\n"))
        .isEqualTo(
            Joiner.on("")
                .join(
                    "function_call(reference, arg_list(positional(reference))), ",
                    "assignment(target, reference)"));
    assertNoErrors();
  }

  public void testList() throws Exception {
    assertThat(parse("[0,f(1),2]"))
        .isEqualTo("list(int, function_call(reference, arg_list(positional(int))), int)");
    assertNoErrors();
  }

  public void testDict() throws Exception {
    assertThat(parse("{1:2,2:f(1),3:4}"))
        .isEqualTo(
            Joiner.on("")
                .join(
                    "dict(",
                    "dict_entry(int, int), ",
                    "dict_entry(int, function_call(reference, arg_list(positional(int)))), ",
                    "dict_entry(int, int)",
                    ")"));
    assertNoErrors();
  }

  public void testArgumentList() throws Exception {
    assertThat(parse("f(0,g(1,2),2)"))
        .isEqualTo(
            Joiner.on("")
                .join(
                    "function_call(reference, arg_list(",
                    "positional(int), ",
                    "positional("
                        + "function_call(reference, arg_list(positional(int), positional(int)))), ",
                    "positional(int)))"));
    assertNoErrors();
  }

  public void testForBreakContinue() throws Exception {
    String parsed =
        parse("def foo():", "  for i in [1, 2]:", "    break", "    continue", "    break");
    assertThat(parsed)
        .isEqualTo(
            Joiner.on("")
                .join(
                    "function_def(parameter_list, ",
                    "stmt_list(for(target, list(int, int), ",
                    "stmt_list(flow, flow, flow))))"));
    assertNoErrors();
  }

  public void testEmptyTuple() throws Exception {
    assertThat(parse("()")).isEqualTo("list");
    assertNoErrors();
  }

  public void testTupleTrailingComma() throws Exception {
    assertThat(parse("(42,)")).isEqualTo("list(int)");
    assertNoErrors();
  }

  public void testSingleton() throws Exception {
    assertThat(parse("(42)")) // not a tuple!
        .isEqualTo("list(int)");
    assertNoErrors();
  }

  public void testDictionaryLiterals() throws Exception {
    assertThat(parse("{1:42}")).isEqualTo("dict(dict_entry(int, int))");
    assertNoErrors();
  }

  public void testDictionaryLiterals1() throws Exception {
    assertThat(parse("{}")).isEqualTo("dict");
    assertNoErrors();
  }

  public void testDictionaryLiterals2() throws Exception {
    assertThat(parse("{1:42,}")).isEqualTo("dict(dict_entry(int, int))");
    assertNoErrors();
  }

  public void testDictionaryLiterals3() throws Exception {
    assertThat(parse("{1:42,2:43,3:44}"))
        .isEqualTo(
            Joiner.on("")
                .join(
                    "dict(",
                    "dict_entry(int, int), ",
                    "dict_entry(int, int), ",
                    "dict_entry(int, int))"));
    assertNoErrors();
  }

  public void testInvalidListComprehensionSyntax() throws Exception {
    assertThat(parse("[x for x for y in ['a']]")).isEqualTo("list_comp(reference, reference)");
    assertContainsErrors();
  }

  public void testListComprehensionEmptyList() throws Exception {
    // At the moment, we just parse the components of comprehension suffixes.
    assertThat(parse("['foo/%s.java' % x for x in []]"))
        .isEqualTo("list_comp(binary_op(string, reference), target, list)");
    assertNoErrors();
  }

  public void testListComprehension() throws Exception {
    assertThat(parse("['foo/%s.java' % x for x in ['bar', 'wiz', 'quux']]"))
        .isEqualTo(
            Joiner.on("")
                .join(
                    "list_comp(binary_op(string, reference), ",
                    "target, ",
                    "list(string, string, string))"));
    assertNoErrors();
  }

  public void testDoesntGetStuck2() throws Exception {
    parse(
        "def foo():",
        "  a = 2 for 4", // parse error
        "  b = [3, 4]",
        "",
        "d = 4 ada", // parse error
        "",
        "def bar():",
        "  a = [3, 4]",
        "  b = 2 + + 5", // parse error
        "");
    assertContainsErrors();
  }

  public void testDoesntGetStuck3() throws Exception {
    parse("load(*)");
    parse("load()");
    parse("load(,)");
    parse("load)");
    parse("load(,");
    parse("load(,\"string\"");
    assertContainsErrors();
  }

  public void testExprAsStatement() throws Exception {
    String parsed =
        parse("li = []", "li.append('a.c')", "\"\"\" string comment \"\"\"", "foo(bar)");
    assertThat(parsed)
        .isEqualTo(
            Joiner.on("")
                .join(
                    "assignment(target, list), ",
                    "function_call(reference, reference, arg_list(positional(string))), ",
                    "string, ",
                    "function_call(reference, arg_list(positional(reference)))"));
    assertNoErrors();
  }

  public void testPrecedence1() {
    assertThat(parse("'%sx' % 'foo' + 'bar'"))
        .isEqualTo("binary_op(binary_op(string, string), string)");
    assertNoErrors();
  }

  public void testPrecedence2() {
    assertThat(parse("('%sx' + 'foo') * 'bar'"))
        .isEqualTo("binary_op(list(binary_op(string, string)), string)");
    assertNoErrors();
  }

  public void testPrecedence3() {
    assertThat(parse("'%sx' % ('foo' + 'bar')"))
        .isEqualTo("binary_op(string, list(binary_op(string, string)))");
    assertNoErrors();
  }

  public void testPrecedence4() throws Exception {
    assertThat(parse("1 + - (2 - 3)"))
        .isEqualTo("binary_op(int, positional(list(binary_op(int, int))))");
    assertNoErrors();
  }

  public void testPrecedence5() throws Exception {
    assertThat(parse("2 * x | y + 1"))
        .isEqualTo("binary_op(binary_op(int, reference), binary_op(reference, int))");
    assertNoErrors();
  }

  public void testNotIsIgnored() throws Exception {
    assertThat(parse("not 'b'")).isEqualTo("string");
    assertNoErrors();
  }

  public void testNotIn() throws Exception {
    assertThat(parse("'a' not in 'b'")).isEqualTo("binary_op(string, string)");
    assertNoErrors();
  }

  public void testParseBuildFileWithSingeRule() throws Exception {
    ASTNode tree =
        createAST(
            "genrule(name = 'foo',",
            "   srcs = ['input.csv'],",
            "   outs = [ 'result.txt',",
            "           'result.log'],",
            "   cmd = 'touch result.txt result.log')");
    List<BuildElement> stmts = getTopLevelNodesOfType(tree, BuildElement.class);
    assertThat(stmts).hasSize(1);
    assertNoErrors();
  }

  public void testParseBuildFileWithMultipleRules() throws Exception {
    ASTNode tree =
        createAST(
            "genrule(name = 'foo',",
            "   srcs = ['input.csv'],",
            "   outs = [ 'result.txt',",
            "           'result.log'],",
            "   srcs = ['input.csv'],",
            "   cmd = 'touch result.txt result.log')",
            "",
            "genrule(name = 'bar',",
            "   outs = [ 'graph.svg'],",
            "   cmd = 'touch graph.svg')");
    List<BuildElement> stmts = getTopLevelNodesOfType(tree, BuildElement.class);
    assertThat(stmts).hasSize(2);
    assertNoErrors();
  }

  public void testMissingComma() throws Exception {
    // missing comma after name='foo'
    parse("genrule(name = 'foo'", "   srcs = ['in'])");
    assertContainsError("',' expected");
  }

  public void testDoubleSemicolon() throws Exception {
    parse("x = 1; ; x = 2;");
    assertContainsError("expected an expression");
  }

  public void testMissingBlock() throws Exception {
    parse("x = 1;", "def foo(x):", "x = 2;\n");
    assertContainsError("'indent' expected");
  }

  public void testFunCallBadSyntax() throws Exception {
    parse("f(1,\n");
    assertContainsError("')' expected");
  }

  public void testFunCallBadSyntax2() throws Exception {
    parse("f(1, 5, ,)\n");
    assertContainsError("expected an expression");
  }

  public void testLoad() throws Exception {
    ASTNode tree = createAST("load('file', 'foo', 'bar',)\n");
    List<LoadStatement> stmts = getTopLevelNodesOfType(tree, LoadStatement.class);
    assertThat(stmts).hasSize(1);

    LoadStatement stmt = stmts.get(0);
    assertThat(stmt.getImportedPath()).isEqualTo("file");
    assertThat(stmt.getImportedSymbolNames()).isEqualTo(new String[] {"foo", "bar"});
    assertNoErrors();
  }

  public void testLoadNoSymbol() throws Exception {
    parse("load('/foo/bar/file')\n");
    assertContainsError("'load' statements must include at least one loaded function");
  }

  public void testFunctionDefinition() throws Exception {
    ASTNode tree =
        createAST(
            "def function(name = 'foo', srcs, outs, *args, **kwargs):",
            "   native.java_library(",
            "     name = name,",
            "     srcs = srcs,",
            "   )",
            "   return");
    List<BuildElement> stmts = getTopLevelNodesOfType(tree, BuildElement.class);
    assertThat(stmts).hasSize(1);
    assertNoErrors();
  }

  public void testFunctionCall() throws Exception {
    ASTNode tree = createAST("function(name = 'foo', srcs, *args, **kwargs)");
    List<BuildElement> stmts = getTopLevelNodesOfType(tree, BuildElement.class);
    assertThat(stmts).hasSize(1);
    assertThat(treeToString(tree))
        .isEqualTo(
            Joiner.on("")
                .join(
                    "function_call(reference, arg_list(",
                    "keyword(string), ",
                    "positional(reference), ",
                    "*(reference), ",
                    "**(reference)))"));
    assertNoErrors();
  }

  public void testConditionalStatement() throws Exception {
    // we don't yet bother specifying which kind of conditionals we hit
    assertThat(parse("if x : y elif a : b else c"))
        .isEqualTo(
            Joiner.on("")
                .join(
                    "if(",
                    "if_part(reference, reference), ",
                    "else_if_part(reference, reference), ",
                    "else_part(reference))"));
  }

  private ASTNode createAST(String... lines) {
    StringBuilder builder = new StringBuilder();
    for (String line : lines) {
      builder.append(line).append("\n");
    }
    return createAST(builder.toString());
  }

  private ASTNode createAST(String text) {
    ParserDefinition definition = new BuildParserDefinition();
    PsiParser parser = definition.createParser(getProject());
    Lexer lexer = definition.createLexer(getProject());
    PsiBuilderImpl psiBuilder =
        new PsiBuilderImpl(
            getProject(), null, definition, lexer, new CharTableImpl(), text, null, null);
    PsiBuilderAdapter adapter =
        new PsiBuilderAdapter(psiBuilder) {
          @Override
          public void error(String messageText) {
            super.error(messageText);
            errors.add(messageText);
          }
        };
    return parser.parse(definition.getFileNodeType(), adapter);
  }

  private String parse(String... lines) {
    StringBuilder builder = new StringBuilder();
    for (String line : lines) {
      builder.append(line).append("\n");
    }
    return parse(builder.toString());
  }

  private String parse(String text) {
    ASTNode tree = createAST(text);
    return treeToString(tree);
  }

  private String treeToString(ASTNode tree) {
    StringBuilder builder = new StringBuilder();
    nodeToString(tree, builder);
    return builder.toString();
  }

  private void nodeToString(ASTNode node, StringBuilder builder) {
    if (node instanceof LeafElement || node.getPsi() == null) {
      return;
    }
    PsiElement[] childPsis = getChildBuildPsis(node);
    if (node instanceof FileASTNode) {
      appendChildren(childPsis, builder, false);
      return;
    }
    builder.append(node.getElementType());
    appendChildren(childPsis, builder, true);
  }

  private void appendChildren(PsiElement[] childPsis, StringBuilder builder, boolean bracket) {
    if (childPsis.length == 0) {
      return;
    }
    if (bracket) {
      builder.append("(");
    }
    nodeToString(childPsis[0].getNode(), builder);
    for (int i = 1; i < childPsis.length; i++) {
      builder.append(", ");
      nodeToString(childPsis[i].getNode(), builder);
    }
    if (bracket) {
      builder.append(")");
    }
  }

  private static <T> List<T> getTopLevelNodesOfType(ASTNode node, Class<T> clazz) {
    return (List)
        Arrays.stream(node.getChildren(null))
            .map(ASTNode::getPsi)
            .filter(psiElement -> clazz.isInstance(psiElement))
            .collect(Collectors.toList());
  }

  private PsiElement[] getChildBuildPsis(ASTNode node) {
    return Arrays.stream(node.getChildren(null))
        .map(ASTNode::getPsi)
        .filter(psiElement -> psiElement instanceof BuildElement)
        .toArray(PsiElement[]::new);
  }

  private void assertNoErrors() {
    assertThat(errors).isEmpty();
  }

  private void assertContainsErrors() {
    assertThat(errors).isNotEmpty();
  }

  private void assertContainsError(String message) {
    assertThat(errors).contains(message);
  }
}
