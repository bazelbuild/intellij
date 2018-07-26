/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.ijwb.typescript;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.idea.blaze.base.BlazeIntegrationTestCase;
import com.google.idea.blaze.base.model.MockBlazeProjectDataBuilder;
import com.google.idea.blaze.base.model.MockBlazeProjectDataManager;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceType;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction;
import com.intellij.lang.javascript.psi.JSDefinitionExpression;
import com.intellij.lang.javascript.psi.JSFile;
import com.intellij.lang.javascript.psi.JSFunction;
import com.intellij.lang.javascript.psi.JSLiteralExpression;
import com.intellij.lang.javascript.psi.JSProperty;
import com.intellij.lang.javascript.psi.ecmal4.JSClass;
import com.intellij.openapi.editor.Caret;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Integration tests for {@link BlazeTypescriptGotoDeclarationHandler}. */
@RunWith(JUnit4.class)
public class BlazeTypescriptGotoDeclarationHandlerTest extends BlazeIntegrationTestCase {
  private JSLiteralExpression googProvideFooBarEsFiveClass;
  private JSFunction fooBarEsFiveClassConstructor;
  private JSFunction fooBarEsFiveClassStaticFoo;
  private JSFunction fooBarEsFiveClassNestedClassStaticFoo;
  private JSFunction fooBarEsFiveClassNonStaticFoo;
  private JSFunction fooBarEsFiveClassNestedClassNonStaticFoo;

  private JSClass<?> fooBarEsFiveClassNestedClass;

  private JSLiteralExpression googProvideFooBarEsSixClass;
  private JSFunction fooBarEsSixClassConstructor;
  private JSFunction fooBarEsSixClassStaticFoo;
  private JSFunction fooBarEsSixClassNonStaticFoo;
  private JSFunction fooBarEsSixClassStaticBar;
  private JSFunction fooBarEsSixClassNonStaticBar;

  private JSDefinitionExpression fooBarEsSixClassEnum;
  private JSProperty fooBarEsSixClassEnumValueFoo;

  private JSLiteralExpression googModuleFooBarClosureClass;
  private JSFunction fooBarClosureClassConstructor;
  private JSFunction fooBarClosureClassStaticFoo;
  private JSFunction fooBarClosureClassNonStaticFoo;
  private JSFunction fooBarClosureClassStaticBar;
  private JSFunction fooBarClosureClassNonStaticBar;

  @Before
  public void init() {
    registerProjectService(
        BlazeProjectDataManager.class,
        new MockBlazeProjectDataManager(
            MockBlazeProjectDataBuilder.builder(workspaceRoot)
                .setWorkspaceLanguageSettings(
                    new WorkspaceLanguageSettings(
                        WorkspaceType.JAVASCRIPT,
                        ImmutableSet.of(LanguageClass.JAVASCRIPT, LanguageClass.TYPESCRIPT)))
                .build()));

    JSFile providesJs =
        (JSFile)
            workspace.createPsiFile(
                new WorkspacePath("foo/bar/provides.js"),
                "goog.provide('foo.bar.ESFiveClass');",
                "/** @constructor */",
                "foo.bar.ESFiveClass = function() {};",
                "/** @public */",
                "foo.bar.ESFiveClass.foo = function() {};",
                "/** @public */",
                "foo.bar.ESFiveClass.prototype.foo = function() {};",
                "foo.bar.ESFiveClass.NestedClass = class {",
                "  /** @public */",
                "  static foo() {}",
                "  /** @public */",
                "  foo() {}",
                "};",
                "goog.provide('foo.bar.ESSixClass');",
                "foo.bar.ESSixClass = class {",
                "  constructor() {}",
                "  /** @public */",
                "  static foo() {}",
                "  /** @public */",
                "  foo() {}",
                "};",
                "/** @public */",
                "foo.bar.ESSixClass.bar = function() {};",
                "/** @public */",
                "foo.bar.ESSixClass.prototype.bar = function() {};",
                "/**  @enum {number} */",
                "foo.bar.ESSixClass.Enum = {",
                "  FOO: 0,",
                "};");

    List<JSLiteralExpression> providesJsLiterals =
        ImmutableList.copyOf(PsiTreeUtil.findChildrenOfType(providesJs, JSLiteralExpression.class));
    assertThat(providesJsLiterals).hasSize(3); // also contains the 0 literal
    googProvideFooBarEsFiveClass = providesJsLiterals.get(0);
    googProvideFooBarEsSixClass = providesJsLiterals.get(1);
    List<JSFunction> providesJsFunctions =
        ImmutableList.copyOf(PsiTreeUtil.findChildrenOfType(providesJs, JSFunction.class));
    assertThat(providesJsFunctions).hasSize(10);
    fooBarEsFiveClassConstructor = providesJsFunctions.get(0);
    fooBarEsFiveClassStaticFoo = providesJsFunctions.get(1);
    fooBarEsFiveClassNonStaticFoo = providesJsFunctions.get(2);
    fooBarEsFiveClassNestedClassStaticFoo = providesJsFunctions.get(3);
    fooBarEsFiveClassNestedClassNonStaticFoo = providesJsFunctions.get(4);
    fooBarEsSixClassConstructor = providesJsFunctions.get(5);
    fooBarEsSixClassStaticFoo = providesJsFunctions.get(6);
    fooBarEsSixClassNonStaticFoo = providesJsFunctions.get(7);
    fooBarEsSixClassStaticBar = providesJsFunctions.get(8);
    fooBarEsSixClassNonStaticBar = providesJsFunctions.get(9);
    List<JSClass<?>> providesJsClasses =
        PsiTreeUtil.findChildrenOfType(providesJs, JSClass.class)
            .stream()
            .map(c -> (JSClass<?>) c)
            .collect(Collectors.toList());
    assertThat(providesJsClasses).hasSize(2); // also contains fooBarEsSixClass, unused
    fooBarEsFiveClassNestedClass = providesJsClasses.get(0);
    List<JSDefinitionExpression> providesJsDefinitions =
        ImmutableList.copyOf(
            PsiTreeUtil.findChildrenOfType(providesJs, JSDefinitionExpression.class));
    assertThat(providesJsDefinitions).isNotEmpty(); // contains every assignment
    fooBarEsSixClassEnum = Iterables.getLast(providesJsDefinitions);
    List<JSProperty> providesJsProperties =
        ImmutableList.copyOf(PsiTreeUtil.findChildrenOfType(providesJs, JSProperty.class));
    assertThat(providesJsProperties).hasSize(1);
    fooBarEsSixClassEnumValueFoo = providesJsProperties.get(0);

    JSFile moduleJs =
        (JSFile)
            workspace.createPsiFile(
                new WorkspacePath("foo/bar/module.js"),
                "goog.module('foo.bar.ClosureClass');",
                "exports = goog.defineClass(null, {",
                "  constructor: function() {},",
                "  statics: {foo: function() {}},",
                "  foo: function() {},",
                "});",
                "/** @public */",
                "exports.bar = function() {};",
                "/** @public */",
                "exports.prototype.bar = function() {};");

    List<JSLiteralExpression> moduleJsLiterals =
        ImmutableList.copyOf(PsiTreeUtil.findChildrenOfType(moduleJs, JSLiteralExpression.class));
    assertThat(moduleJsLiterals.size()).isAtLeast(1);
    googModuleFooBarClosureClass = moduleJsLiterals.get(0);
    List<JSFunction> moduleJsFunctions =
        ImmutableList.copyOf(PsiTreeUtil.findChildrenOfType(moduleJs, JSFunction.class));
    assertThat(moduleJsFunctions).hasSize(5);
    fooBarClosureClassConstructor = moduleJsFunctions.get(0);
    fooBarClosureClassStaticFoo = moduleJsFunctions.get(1);
    fooBarClosureClassNonStaticFoo = moduleJsFunctions.get(2);
    fooBarClosureClassStaticBar = moduleJsFunctions.get(3);
    fooBarClosureClassNonStaticBar = moduleJsFunctions.get(4);

    // Generated from this:
    // js_library(
    //     name = "library",
    //     srcs = [
    //         "module.js",
    //         "provides.js",
    //     ],
    //     deps = ["//javascript/closure"],
    // )
    workspace.createPsiFile(
        new WorkspacePath("foo/bar/library.d.ts"),
        "",
        "//!! Processing provides [foo.bar.ESFiveClass, foo.bar.ESSixClass] from input "
            + "foo/bar/provides.js",
        "//!! Processing provides [foo.bar.ClosureClass] from input foo/bar/module.js",
        "declare namespace ಠ_ಠ.clutz {",
        "  class module$exports$foo$bar$ClosureClass extends "
            + "module$exports$foo$bar$ClosureClass_Instance {",
        "    static bar ( ) : void ;",
        "    static foo ( ) : void ;",
        "  }",
        "  class module$exports$foo$bar$ClosureClass_Instance {",
        "    private noStructuralTyping_: any;",
        "    bar ( ) : void ;",
        "    foo ( ) : void ;",
        "  }",
        "}",
        "declare module 'goog:foo.bar.ClosureClass' {",
        "  import alias = ಠ_ಠ.clutz.module$exports$foo$bar$ClosureClass;",
        "  export default alias;",
        "}",
        "declare namespace ಠ_ಠ.clutz.foo.bar {",
        "  class ESFiveClass extends ESFiveClass_Instance {",
        "    static foo ( ) : void ;",
        "  }",
        "  class ESFiveClass_Instance {",
        "    private noStructuralTyping_: any;",
        "    foo ( ) : void ;",
        "  }",
        "}",
        "declare namespace ಠ_ಠ.clutz.foo.bar.ESFiveClass {",
        "  class NestedClass extends NestedClass_Instance {",
        "    static foo ( ) : void ;",
        "  }",
        "  class NestedClass_Instance {",
        "    private noStructuralTyping_: any;",
        "    foo ( ) : void ;",
        "  }",
        "}",
        "declare module 'goog:foo.bar.ESFiveClass' {",
        "  import alias = ಠ_ಠ.clutz.foo.bar.ESFiveClass;",
        "  export default alias;",
        "}",
        "declare namespace ಠ_ಠ.clutz.foo.bar {",
        "  class ESSixClass extends ESSixClass_Instance {",
        "    static bar ( ) : void ;",
        "    static foo ( ) : void ;",
        "  }",
        "  class ESSixClass_Instance {",
        "    private noStructuralTyping_: any;",
        "    bar ( ) : void ;",
        "    foo ( ) : void ;",
        "  }",
        "}",
        "declare namespace ಠ_ಠ.clutz.foo.bar.ESSixClass {",
        "  enum Enum {",
        "    FOO = 0.0 ,",
        "  }",
        "}",
        "declare module 'goog:foo.bar.ESSixClass' {",
        "  import alias = ಠ_ಠ.clutz.foo.bar.ESSixClass;",
        "  export default alias;",
        "}");
  }

  @Test
  public void testGotoImportStatements() {
    configureUserTs(
        "import E<caret>SFiveClass from 'goog:foo.bar.ESFiveClass';",
        "import E<caret>SSixClass from 'goog:foo.bar.ESSixClass';",
        "import C<caret>losureClass from 'goog:foo.bar.ClosureClass';");
    assertThatCaret(0).resolvesTo(googProvideFooBarEsFiveClass);
    assertThatCaret(1).resolvesTo(googProvideFooBarEsSixClass);
    assertThatCaret(2).resolvesTo(googModuleFooBarClosureClass);
  }

  @Test
  public void testGotoClasses() {
    configureUserTs(
        "import ESFiveClass from 'goog:foo.bar.ESFiveClass';",
        "import ESSixClass from 'goog:foo.bar.ESSixClass';",
        "import ClosureClass from 'goog:foo.bar.ClosureClass';",
        "var a: E<caret>SFiveClass;",
        "var b: ESFiveClass.N<caret>estedClass;",
        "var c: E<caret>SSixClass;",
        "var d: C<caret>losureClass;");
    assertThatCaret(0).resolvesTo(googProvideFooBarEsFiveClass);
    assertThatCaret(1).resolvesTo(fooBarEsFiveClassNestedClass);
    assertThatCaret(2).resolvesTo(googProvideFooBarEsSixClass);
    assertThatCaret(3).resolvesTo(googModuleFooBarClosureClass);
  }

  @Test
  public void testGotoConstructors() {
    configureUserTs(
        "import ESFiveClass from 'goog:foo.bar.ESFiveClass';",
        "import ESSixClass from 'goog:foo.bar.ESSixClass';",
        "import ClosureClass from 'goog:foo.bar.ClosureClass';",
        "new E<caret>SFiveClass();",
        "new ESFiveClass.N<caret>estedClass();",
        "new E<caret>SSixClass();",
        "new C<caret>losureClass();");
    assertThatCaret(0).resolvesTo(fooBarEsFiveClassConstructor);
    assertThatCaret(1).resolvesTo(fooBarEsFiveClassNestedClass); // no constructor
    assertThatCaret(2).resolvesTo(fooBarEsSixClassConstructor);
    assertThatCaret(3).resolvesTo(fooBarClosureClassConstructor);
  }

  @Test
  public void testGotoStaticMethods() {
    configureUserTs(
        "import ESFiveClass from 'goog:foo.bar.ESFiveClass';",
        "import ESSixClass from 'goog:foo.bar.ESSixClass';",
        "import ClosureClass from 'goog:foo.bar.ClosureClass';",
        "ESFiveClass.f<caret>oo();",
        "ESFiveClass.NestedClass.f<caret>oo();",
        "ESSixClass.f<caret>oo();",
        "ESSixClass.b<caret>ar();",
        "ClosureClass.f<caret>oo();",
        "ClosureClass.b<caret>ar();");
    assertThatCaret(0).resolvesTo(fooBarEsFiveClassStaticFoo);
    assertThatCaret(1).resolvesTo(fooBarEsFiveClassNestedClassStaticFoo);
    assertThatCaret(2).resolvesTo(fooBarEsSixClassStaticFoo); // in class definition
    assertThatCaret(3).resolvesTo(fooBarEsSixClassStaticBar); // attached afterwards
    assertThatCaret(4).resolvesTo(fooBarClosureClassStaticFoo); // in class definition
    assertThatCaret(5).resolvesTo(fooBarClosureClassStaticBar); // attached afterwards
  }

  @Test
  public void testGotoNonStaticMethods() {
    configureUserTs(
        "import ESFiveClass from 'goog:foo.bar.ESFiveClass';",
        "import ESSixClass from 'goog:foo.bar.ESSixClass';",
        "import ClosureClass from 'goog:foo.bar.ClosureClass';",
        "new ESFiveClass().f<caret>oo();",
        "new ESFiveClass.NestedClass().f<caret>oo();",
        "new ESSixClass().f<caret>oo();",
        "new ESSixClass().b<caret>ar();",
        "new ClosureClass().f<caret>oo();",
        "new ClosureClass().b<caret>ar();");
    assertThatCaret(0).resolvesTo(fooBarEsFiveClassNonStaticFoo);
    assertThatCaret(1).resolvesTo(fooBarEsFiveClassNestedClassNonStaticFoo);
    assertThatCaret(2).resolvesTo(fooBarEsSixClassNonStaticFoo); // in class definition
    assertThatCaret(3).resolvesTo(fooBarEsSixClassNonStaticBar); // attached afterwards
    assertThatCaret(4).resolvesTo(fooBarClosureClassNonStaticFoo); // in class definition
    assertThatCaret(5).resolvesTo(fooBarClosureClassNonStaticBar); // attached afterwards
  }

  @Test
  public void testGotoEnum() {
    configureUserTs(
        "import ESSixClass from 'goog:foo.bar.ESSixClass';", "ESSixClass.E<caret>num.F<caret>OO;");
    assertThatCaret(0).resolvesTo(fooBarEsSixClassEnum);
    assertThatCaret(1).resolvesTo(fooBarEsSixClassEnumValueFoo);
  }

  private void configureUserTs(String... contents) {
    JSFile userTs =
        (JSFile) workspace.createPsiFile(new WorkspacePath("foo/bar/user.ts"), contents);
    testFixture.configureFromExistingVirtualFile(userTs.getVirtualFile());
  }

  private interface Subject {
    void resolvesTo(PsiElement expected);
  }

  private Subject assertThatCaret(int index) {
    Caret caret = testFixture.getEditor().getCaretModel().getAllCarets().get(index);
    return expected -> {
      PsiElement actual =
          GotoDeclarationAction.findTargetElement(
              getProject(), testFixture.getEditor(), caret.getOffset());
      assertThat(actual).isEqualTo(expected);
    };
  }
}
