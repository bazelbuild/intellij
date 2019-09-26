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
package com.google.idea.blaze.typescript;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.BlazeIntegrationTestCase;
import com.google.idea.blaze.base.model.MockBlazeProjectDataBuilder;
import com.google.idea.blaze.base.model.MockBlazeProjectDataManager;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceType;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction;
import com.intellij.lang.ecmascript6.psi.ES6ClassExpression;
import com.intellij.lang.javascript.psi.JSDefinitionExpression;
import com.intellij.lang.javascript.psi.JSFile;
import com.intellij.lang.javascript.psi.JSFunction;
import com.intellij.lang.javascript.psi.JSFunctionExpression;
import com.intellij.lang.javascript.psi.JSLiteralExpression;
import com.intellij.lang.javascript.psi.JSProperty;
import com.intellij.psi.PsiElement;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Integration tests for {@link BlazeTypescriptGotoDeclarationHandler}.
 *
 * <p>Handles .d.ts files generated from old CLs. Uses FILE_GENERATED_FROM_JS_COMMENT.
 */
@RunWith(JUnit4.class)
public class OldBlazeTypescriptGotoDeclarationHandlerTest extends BlazeIntegrationTestCase {
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

    PsiElement esFiveClass = getElementUnderCaret(0);
    assertThat(esFiveClass).isInstanceOf(JSLiteralExpression.class);
    assertThat(((JSLiteralExpression) esFiveClass).getStringValue())
        .isEqualTo("foo.bar.ESFiveClass");
    assertThat(esFiveClass.getParent().getParent().getText())
        .isEqualTo("goog.provide('foo.bar.ESFiveClass')");

    PsiElement esSixClass = getElementUnderCaret(1);
    assertThat(esSixClass).isInstanceOf(JSLiteralExpression.class);
    assertThat(((JSLiteralExpression) esSixClass).getStringValue()).isEqualTo("foo.bar.ESSixClass");
    assertThat(esSixClass.getParent().getParent().getText())
        .isEqualTo("goog.provide('foo.bar.ESSixClass')");

    PsiElement closureClass = getElementUnderCaret(2);
    assertThat(closureClass).isInstanceOf(JSLiteralExpression.class);
    assertThat(((JSLiteralExpression) closureClass).getStringValue())
        .isEqualTo("foo.bar.ClosureClass");
    assertThat(closureClass.getParent().getParent().getText())
        .isEqualTo("goog.module('foo.bar.ClosureClass')");
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

    PsiElement esFiveClass = getElementUnderCaret(0);
    assertThat(esFiveClass).isInstanceOf(JSLiteralExpression.class);
    assertThat(((JSLiteralExpression) esFiveClass).getStringValue())
        .isEqualTo("foo.bar.ESFiveClass");
    assertThat(esFiveClass.getParent().getParent().getText())
        .isEqualTo("goog.provide('foo.bar.ESFiveClass')");

    PsiElement nestedClass = getElementUnderCaret(1);
    assertThat(nestedClass).isInstanceOf(ES6ClassExpression.class);
    assertThat(nestedClass.getParent().getText())
        .isEqualTo(
            String.join(
                "\n",
                "foo.bar.ESFiveClass.NestedClass = class {",
                "  /** @public */",
                "  static foo() {}",
                "  /** @public */",
                "  foo() {}",
                "}"));

    PsiElement esSixClass = getElementUnderCaret(2);
    assertThat(esSixClass).isInstanceOf(JSLiteralExpression.class);
    assertThat(((JSLiteralExpression) esSixClass).getStringValue()).isEqualTo("foo.bar.ESSixClass");
    assertThat(esSixClass.getParent().getParent().getText())
        .isEqualTo("goog.provide('foo.bar.ESSixClass')");

    PsiElement closureClass = getElementUnderCaret(3);
    assertThat(closureClass).isInstanceOf(JSLiteralExpression.class);
    assertThat(((JSLiteralExpression) closureClass).getStringValue())
        .isEqualTo("foo.bar.ClosureClass");
    assertThat(closureClass.getParent().getParent().getText())
        .isEqualTo("goog.module('foo.bar.ClosureClass')");
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

    PsiElement esFiveClassConstructor = getElementUnderCaret(0);
    assertThat(esFiveClassConstructor).isInstanceOf(JSFunctionExpression.class);
    assertThat(esFiveClassConstructor.getParent().getText())
        .isEqualTo("foo.bar.ESFiveClass = function() {}");

    PsiElement nestedClass = getElementUnderCaret(1); // no constructor
    assertThat(nestedClass).isInstanceOf(ES6ClassExpression.class);
    assertThat(nestedClass.getParent().getText())
        .isEqualTo(
            String.join(
                "\n",
                "foo.bar.ESFiveClass.NestedClass = class {",
                "  /** @public */",
                "  static foo() {}",
                "  /** @public */",
                "  foo() {}",
                "}"));

    PsiElement esSixClassConstructor = getElementUnderCaret(2);
    assertThat(esSixClassConstructor).isInstanceOf(JSFunction.class);
    assertThat(esSixClassConstructor.getText()).isEqualTo("constructor() {}");
    assertThat(esSixClassConstructor.getParent().getParent().getText())
        .isEqualTo(
            String.join(
                "\n",
                "foo.bar.ESSixClass = class {",
                "  constructor() {}",
                "  /** @public */",
                "  static foo() {}",
                "  /** @public */",
                "  foo() {}",
                "}"));

    PsiElement closureClassConstructor = getElementUnderCaret(3);
    assertThat(closureClassConstructor).isInstanceOf(JSFunctionExpression.class);
    assertThat(closureClassConstructor.getParent().getText())
        .isEqualTo("constructor: function() {}");
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

    PsiElement esFiveClassFoo = getElementUnderCaret(0);
    assertThat(esFiveClassFoo).isInstanceOf(JSFunctionExpression.class);
    assertThat(esFiveClassFoo.getParent().getText())
        .isEqualTo("foo.bar.ESFiveClass.foo = function() {}");

    PsiElement nestedClassFoo = getElementUnderCaret(1);
    assertThat(nestedClassFoo).isInstanceOf(JSFunction.class);
    assertThat(nestedClassFoo.getText())
        .isEqualTo(
            String.join(
                "\n", //
                "/** @public */",
                "  static foo() {}"));
    assertThat(nestedClassFoo.getParent().getParent().getText())
        .isEqualTo(
            String.join(
                "\n",
                "foo.bar.ESFiveClass.NestedClass = class {",
                "  /** @public */",
                "  static foo() {}",
                "  /** @public */",
                "  foo() {}",
                "}"));

    PsiElement esSixClassFoo = getElementUnderCaret(2); // in class definition
    assertThat(esSixClassFoo).isInstanceOf(JSFunction.class);
    assertThat(esSixClassFoo.getText())
        .isEqualTo(
            String.join(
                "\n", //
                "/** @public */",
                "  static foo() {}"));
    assertThat(esSixClassFoo.getParent().getParent().getText())
        .isEqualTo(
            String.join(
                "\n",
                "foo.bar.ESSixClass = class {",
                "  constructor() {}",
                "  /** @public */",
                "  static foo() {}",
                "  /** @public */",
                "  foo() {}",
                "}"));

    PsiElement esSixClassBar = getElementUnderCaret(3); // attached afterwards
    assertThat(esSixClassBar).isInstanceOf(JSFunctionExpression.class);
    assertThat(esSixClassBar.getParent().getText())
        .isEqualTo("foo.bar.ESSixClass.bar = function() {}");

    PsiElement closureClassFoo = getElementUnderCaret(4); // in class definition
    assertThat(closureClassFoo).isInstanceOf(JSFunctionExpression.class);
    assertThat(closureClassFoo.getParent().getParent().getParent().getText())
        .isEqualTo("statics: {foo: function() {}}");

    PsiElement closureClassBar = getElementUnderCaret(5); // attached afterwards
    assertThat(closureClassBar).isInstanceOf(JSFunctionExpression.class);
    assertThat(closureClassBar.getParent().getText()).isEqualTo("exports.bar = function() {}");
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

    PsiElement esFiveClassFoo = getElementUnderCaret(0);
    assertThat(esFiveClassFoo).isInstanceOf(JSFunctionExpression.class);
    assertThat(esFiveClassFoo.getParent().getText())
        .isEqualTo("foo.bar.ESFiveClass.prototype.foo = function() {}");

    PsiElement nestedClassFoo = getElementUnderCaret(1);
    assertThat(nestedClassFoo).isInstanceOf(JSFunction.class);
    assertThat(nestedClassFoo.getText())
        .isEqualTo(
            String.join(
                "\n", //
                "/** @public */",
                "  foo() {}"));
    assertThat(nestedClassFoo.getParent().getParent().getText())
        .isEqualTo(
            String.join(
                "\n",
                "foo.bar.ESFiveClass.NestedClass = class {",
                "  /** @public */",
                "  static foo() {}",
                "  /** @public */",
                "  foo() {}",
                "}"));

    PsiElement esSixClassFoo = getElementUnderCaret(2); // in class definition
    assertThat(esSixClassFoo).isInstanceOf(JSFunction.class);
    assertThat(esSixClassFoo.getText())
        .isEqualTo(
            String.join(
                "\n", //
                "/** @public */",
                "  foo() {}"));
    assertThat(esSixClassFoo.getParent().getParent().getText())
        .isEqualTo(
            String.join(
                "\n",
                "foo.bar.ESSixClass = class {",
                "  constructor() {}",
                "  /** @public */",
                "  static foo() {}",
                "  /** @public */",
                "  foo() {}",
                "}"));

    PsiElement esSixClassBar = getElementUnderCaret(3); // attached afterwards
    assertThat(esSixClassBar).isInstanceOf(JSFunctionExpression.class);
    assertThat(esSixClassBar.getParent().getText())
        .isEqualTo("foo.bar.ESSixClass.prototype.bar = function() {}");

    PsiElement closureClassFoo = getElementUnderCaret(4); // in class definition
    assertThat(closureClassFoo).isInstanceOf(JSFunctionExpression.class);
    assertThat(closureClassFoo.getParent().getText()).isEqualTo("foo: function() {}");
    assertThat(closureClassFoo.getParent().getParent().getText())
        .isEqualTo(
            String.join(
                "\n",
                "{",
                "  constructor: function() {},",
                "  statics: {foo: function() {}},",
                "  foo: function() {},",
                "}"));

    PsiElement closureClassBar = getElementUnderCaret(5); // attached afterwards
    assertThat(closureClassBar).isInstanceOf(JSFunctionExpression.class);
    assertThat(closureClassBar.getParent().getText())
        .isEqualTo("exports.prototype.bar = function() {}");
  }

  @Test
  public void testGotoEnum() {
    configureUserTs(
        "import ESSixClass from 'goog:foo.bar.ESSixClass';", //
        "ESSixClass.E<caret>num.F<caret>OO;");

    PsiElement esSixClassEnum = getElementUnderCaret(0);
    assertThat(esSixClassEnum).isInstanceOf(JSDefinitionExpression.class);
    assertThat(esSixClassEnum.getText()).isEqualTo("foo.bar.ESSixClass.Enum");
    assertThat(esSixClassEnum.getParent().getText())
        .isEqualTo(
            String.join(
                "\n", //
                "foo.bar.ESSixClass.Enum = {",
                "  FOO: 0,",
                "}"));

    PsiElement esSixClassEnumFoo = getElementUnderCaret(1);
    assertThat(esSixClassEnumFoo).isInstanceOf(JSProperty.class);
    assertThat(esSixClassEnumFoo.getText()).isEqualTo("FOO: 0");
  }

  private void configureUserTs(String... contents) {
    JSFile userTs =
        (JSFile) workspace.createPsiFile(new WorkspacePath("foo/bar/user.ts"), contents);
    testFixture.configureFromExistingVirtualFile(userTs.getVirtualFile());
  }

  private PsiElement getElementUnderCaret(int index) {
    return GotoDeclarationAction.findTargetElement(
        getProject(),
        testFixture.getEditor(),
        testFixture.getEditor().getCaretModel().getAllCarets().get(index).getOffset());
  }
}
