/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.javascript.run.producers;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.idea.blaze.base.ideinfo.JsIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetMapBuilder;
import com.google.idea.blaze.base.model.MockBlazeProjectDataBuilder;
import com.google.idea.blaze.base.model.MockBlazeProjectDataManager;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.run.producers.BlazeRunConfigurationProducerTestCase;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.intellij.execution.lineMarker.RunLineMarkerContributor;
import com.intellij.execution.lineMarker.RunLineMarkerContributor.Info;
import com.intellij.icons.AllIcons.RunConfigurations.TestState;
import com.intellij.lang.javascript.psi.JSCallExpression;
import com.intellij.lang.javascript.psi.JSLiteralExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import javax.swing.Icon;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Integration tests for {@link BlazeJavaScriptTestRunLineMarkerContributor} */
@RunWith(JUnit4.class)
public class BlazeJavaScriptTestRunLineMarkerContributorTest
    extends BlazeRunConfigurationProducerTestCase {
  private RunLineMarkerContributor markerContributor;

  @Before
  public void setup() {
    // Must happen after BlazeRunConfigurationProducerTestCase#doSetup,
    // because icons are statically loaded.
    markerContributor = new BlazeJavaScriptTestRunLineMarkerContributor();
  }

  @Test
  public void testGetClosureTestInfo() throws Throwable {
    TargetMapBuilder targetMapBuilder =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setKind("jsunit_test")
                    .setLabel("//foo/bar:foo_test_debug")
                    .setBuildFile(sourceRoot("foo/bar/BUILD"))
                    .addSource(sourceRoot("foo/bar/foo_test.js"))
                    .setJsInfo(JsIdeInfo.builder().addSource(sourceRoot("foo/bar/foo_test.js"))))
            .addTarget(
                TargetIdeInfo.builder()
                    .setKind("web_test")
                    .setLabel("//foo/bar:foo_test_chrome-linux")
                    .setBuildFile(sourceRoot("foo/bar/BUILD"))
                    .addDependency("//foo/bar:foo_test_debug"))
            .addTarget(
                TargetIdeInfo.builder()
                    .setKind("web_test")
                    .setLabel("//foo/bar:foo_test_firefox-linux")
                    .setBuildFile(sourceRoot("foo/bar/BUILD"))
                    .addDependency("//foo/bar:foo_test_debug"));
    MockBlazeProjectDataBuilder builder =
        MockBlazeProjectDataBuilder.builder(workspaceRoot).setTargetMap(targetMapBuilder.build());
    registerProjectService(
        BlazeProjectDataManager.class, new MockBlazeProjectDataManager(builder.build()));

    PsiFile closureTestFile =
        createAndIndexFile(
            WorkspacePath.createIfValid("foo/bar/foo_test.js"),
            "function testFoo() {}",
            "function testBar() {}",
            "function notTest() {}");

    ImmutableList<LeafPsiElement> elements =
        PsiTreeUtil.findChildrenOfType(closureTestFile, LeafPsiElement.class).stream()
            .filter(e -> markerContributor.getInfo(e) != null)
            .collect(ImmutableList.toImmutableList());

    assertThat(elements).hasSize(2);
    assertThatClosureElement(elements.get(0)).hasName("testFoo").hasIcon(TestState.Run);
    assertThatClosureElement(elements.get(1)).hasName("testBar").hasIcon(TestState.Run);
  }

  @Test
  public void testGetClosureTestSuiteInfo() throws Throwable {
    TargetMapBuilder targetMapBuilder =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setKind("jsunit_test")
                    .setLabel("//foo/bar:foo_test_debug")
                    .setBuildFile(sourceRoot("foo/bar/BUILD"))
                    .addSource(sourceRoot("foo/bar/foo_test.js"))
                    .setJsInfo(JsIdeInfo.builder().addSource(sourceRoot("foo/bar/foo_test.js"))))
            .addTarget(
                TargetIdeInfo.builder()
                    .setKind("web_test")
                    .setLabel("//foo/bar:foo_test_chrome-linux")
                    .setBuildFile(sourceRoot("foo/bar/BUILD"))
                    .addDependency("//foo/bar:foo_test_debug"))
            .addTarget(
                TargetIdeInfo.builder()
                    .setKind("web_test")
                    .setLabel("//foo/bar:foo_test_firefox-linux")
                    .setBuildFile(sourceRoot("foo/bar/BUILD"))
                    .addDependency("//foo/bar:foo_test_debug"));
    MockBlazeProjectDataBuilder builder =
        MockBlazeProjectDataBuilder.builder(workspaceRoot).setTargetMap(targetMapBuilder.build());
    registerProjectService(
        BlazeProjectDataManager.class, new MockBlazeProjectDataManager(builder.build()));

    PsiFile closureTestFile =
        createAndIndexFile(
            WorkspacePath.createIfValid("foo/bar/foo_test.js"),
            "goog.module('test');",
            "goog.setTestOnly();",
            "const testSuite = goog.require('goog.testing.testSuite');",
            "testSuite({",
            "  testFoo() {},",
            "  testBar() {},",
            "  notTest() {},",
            "});");

    ImmutableList<LeafPsiElement> elements =
        PsiTreeUtil.findChildrenOfType(closureTestFile, LeafPsiElement.class).stream()
            .filter(e -> markerContributor.getInfo(e) != null)
            .collect(ImmutableList.toImmutableList());

    assertThat(elements).hasSize(2);
    assertThatClosureElement(elements.get(0)).hasName("testFoo").hasIcon(TestState.Run);
    assertThatClosureElement(elements.get(1)).hasName("testBar").hasIcon(TestState.Run);
  }

  @Test
  public void testGetJasmineTestInfo() throws Throwable {
    TargetMapBuilder targetMapBuilder =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setKind("web_test")
                    .setLabel("//foo/bar:foo_test_chrome-linux")
                    .setBuildFile(sourceRoot("foo/bar/BUILD"))
                    .addDependency("//foo/bar:foo_test_wrapped_test"))
            .addTarget(
                TargetIdeInfo.builder()
                    .setKind("_nodejs_test")
                    .setLabel("//foo/bar:foo_test_wrapped_test")
                    .setBuildFile(sourceRoot("foo/bar/BUILD"))
                    .addDependency("//foo/bar:foo_test_wrapped_test_jasmine_node_module")
                    .setJsInfo(JsIdeInfo.builder()))
            .addTarget(
                TargetIdeInfo.builder()
                    .setKind("_nodejs_module")
                    .setLabel("//foo/bar:foo_test_wrapped_test_jasmine_node_module")
                    .setBuildFile(sourceRoot("foo/bar/BUILD"))
                    .addDependency("//foo/bar:foo_test_wrapped_test_srcs")
                    .addSource(sourceRoot("foo/bar/foo_test.js"))
                    .addSource(sourceRoot("foo/bar/bar_test.js"))
                    .setJsInfo(
                        JsIdeInfo.builder()
                            .addSource(sourceRoot("foo/bar/foo_test.js"))
                            .addSource(sourceRoot("foo/bar/bar_test.js"))));
    MockBlazeProjectDataBuilder builder =
        MockBlazeProjectDataBuilder.builder(workspaceRoot).setTargetMap(targetMapBuilder.build());
    registerProjectService(
        BlazeProjectDataManager.class, new MockBlazeProjectDataManager(builder.build()));

    PsiFile jasmineTestFile =
        createAndIndexFile(
            WorkspacePath.createIfValid("foo/bar/foo_test.js"),
            "const webTest = require('web-test');",
            "describe('foo', function() {",
            "  it('should pass', function() {});",
            "  it('should pass too', function() {});",
            "  describe('nested', function() {",
            "    describe('double nested', function() {",
            "      it('should be nested', function() {});",
            "      it('should be nested too', function() {});",
            "    });",
            "  });",
            "});");

    ImmutableList<LeafPsiElement> elements =
        PsiTreeUtil.findChildrenOfType(jasmineTestFile, LeafPsiElement.class).stream()
            .filter(e -> markerContributor.getInfo(e) != null)
            .collect(ImmutableList.toImmutableList());

    assertThat(elements).hasSize(7);
    assertThatJasmineElement(elements.get(0))
        .isTestSuite()
        .hasName("foo")
        .hasIcon(TestState.Run_run);
    assertThatJasmineElement(elements.get(1))
        .isTestCase()
        .hasName("should pass")
        .hasIcon(TestState.Run);
    assertThatJasmineElement(elements.get(2))
        .isTestCase()
        .hasName("should pass too")
        .hasIcon(TestState.Run);
    assertThatJasmineElement(elements.get(3))
        .isTestSuite()
        .hasName("nested")
        .hasIcon(TestState.Run_run);
    assertThatJasmineElement(elements.get(4))
        .isTestSuite()
        .hasName("double nested")
        .hasIcon(TestState.Run_run);
    assertThatJasmineElement(elements.get(5))
        .isTestCase()
        .hasName("should be nested")
        .hasIcon(TestState.Run);
    assertThatJasmineElement(elements.get(6))
        .isTestCase()
        .hasName("should be nested too")
        .hasIcon(TestState.Run);
  }

  private interface Subject {
    @CanIgnoreReturnValue
    Subject isTestSuite();

    @CanIgnoreReturnValue
    Subject isTestCase();

    @CanIgnoreReturnValue
    Subject hasName(String name);

    @CanIgnoreReturnValue
    Subject hasIcon(Icon icon);
  }

  private Subject assertThatClosureElement(LeafPsiElement element) {
    Info info = markerContributor.getInfo(element);
    assertThat(info).isNotNull();
    return new Subject() {
      @Override
      public Subject isTestSuite() {
        return this;
      }

      @Override
      public Subject isTestCase() {
        return this;
      }

      @Override
      public Subject hasName(String name) {
        assertThat(element.getText()).isEqualTo(name);
        return this;
      }

      @Override
      public Subject hasIcon(Icon icon) {
        assertThat(info.icon).isEqualTo(icon);
        return this;
      }
    };
  }

  private Subject assertThatJasmineElement(LeafPsiElement element) {
    Info info = markerContributor.getInfo(element);
    assertThat(info).isNotNull();
    return new Subject() {
      @Override
      public Subject isTestSuite() {
        assertThat(element.getText()).isEqualTo("describe");
        return this;
      }

      @Override
      public Subject isTestCase() {
        assertThat(element.getText()).isEqualTo("it");
        return this;
      }

      @Override
      public Subject hasName(String name) {
        PsiElement grandParent = element.getParent().getParent();
        assertThat(grandParent).isInstanceOf(JSCallExpression.class);
        JSCallExpression call = (JSCallExpression) grandParent;
        assertThat(call.getArguments()[0]).isInstanceOf(JSLiteralExpression.class);
        JSLiteralExpression literal = (JSLiteralExpression) call.getArguments()[0];
        assertThat(literal.getStringValue()).isEqualTo(name);
        return this;
      }

      @Override
      public Subject hasIcon(Icon icon) {
        assertThat(info.icon).isEqualTo(icon);
        return this;
      }
    };
  }
}
