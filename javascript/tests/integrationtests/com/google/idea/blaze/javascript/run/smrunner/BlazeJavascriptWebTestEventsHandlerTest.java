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
package com.google.idea.blaze.javascript.run.smrunner;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.BlazeIntegrationTestCase;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.JsIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.ideinfo.TargetMapBuilder;
import com.google.idea.blaze.base.model.MockBlazeProjectDataBuilder;
import com.google.idea.blaze.base.model.MockBlazeProjectDataManager;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceType;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.intellij.execution.Location;
import com.intellij.javascript.testFramework.jasmine.JasmineFileStructure;
import com.intellij.javascript.testFramework.jasmine.JasmineFileStructureBuilder;
import com.intellij.javascript.testFramework.jasmine.JasmineSpecStructure;
import com.intellij.javascript.testFramework.jasmine.JasmineSuiteStructure;
import com.intellij.lang.javascript.psi.JSCallExpression;
import com.intellij.lang.javascript.psi.JSFile;
import com.intellij.lang.javascript.psi.JSFunction;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.search.GlobalSearchScope;
import java.util.List;
import javax.annotation.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Integration tests for {@link BlazeJavascriptTestEventsHandler} with js_web_test. */
@RunWith(JUnit4.class)
public class BlazeJavascriptWebTestEventsHandlerTest extends BlazeIntegrationTestCase {

  private final BlazeJavascriptTestEventsHandler handler = new BlazeJavascriptTestEventsHandler();

  @Test
  public void testClosureTestResolves() {
    TargetMap targetMap =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//foo/bar:foo_test_chrome-linux")
                    .setKind("js_web_test")
                    .setBuildFile(src("foo/bar/BUILD"))
                    .addDependency("//foo/bar:foo_test_debug"))
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//foo/bar:foo_test_debug")
                    .setKind("jsunit_test")
                    .setBuildFile(src("foo/bar/BUILD"))
                    .addSource(src("foo/bar/foo_test.js"))
                    .addSource(src("foo/bar/bar_test.js"))
                    .setJsInfo(
                        JsIdeInfo.builder()
                            .addSource(src("foo/bar/foo_test.js"))
                            .addSource(src("foo/bar/bar_test.js"))))
            .build();

    registerProjectService(
        BlazeProjectDataManager.class,
        new MockBlazeProjectDataManager(
            MockBlazeProjectDataBuilder.builder(workspaceRoot)
                .setTargetMap(targetMap)
                .setWorkspaceLanguageSettings(
                    new WorkspaceLanguageSettings(
                        WorkspaceType.JAVASCRIPT, ImmutableSet.of(LanguageClass.JAVASCRIPT)))
                .build()));

    JSFile fooFile =
        (JSFile)
            workspace.createPsiFile(
                new WorkspacePath("foo/bar/foo_test.js"), "function testFoo() {}");

    JSFile barFile =
        (JSFile)
            workspace.createPsiFile(
                new WorkspacePath("foo/bar/bar_test.js"),
                "goog.module('bar');",
                "goog.setTestOnly();",
                "const testSuite = goog.require('goog.testing.testSuite');",
                "testSuite({",
                "  testBar() {},",
                "});");

    workspace.createFile(
        new WorkspacePath("foo/bar/BUILD"),
        "jsunit_test_suite(",
        "    name = 'foo_test',",
        "    srcs = [",
        "        'foo_test.js',",
        "        'bar_test.js',",
        "    ],",
        "    browsers = ['//testing/web/browsers:chrome-linux'],",
        ")");

    Label label = Label.create("//foo/bar:foo_test_chrome-linux");
    Kind kind = Kind.fromRuleName("js_web_test");

    {
      String url = handler.suiteLocationUrl(label, kind, "workspace/foo/bar/foo_test");
      Location<?> location = getLocation(url);
      assertThat(location).isNotNull();
      assertThat(location.getPsiElement()).isEqualTo(fooFile);
    }
    {
      String url =
          handler.testLocationUrl(label, kind, "workspace/foo/bar/foo_test", "testFoo", null);
      Location<?> location = getLocation(url);
      assertThat(location).isNotNull();
      assertThat(location.getPsiElement()).isInstanceOf(JSFunction.class);
      JSFunction function = (JSFunction) location.getPsiElement();
      assertThat(function.getName()).isEqualTo("testFoo");
    }
    {
      String url = handler.suiteLocationUrl(label, kind, "workspace/foo/bar/bar_test");
      Location<?> location = getLocation(url);
      assertThat(location).isNotNull();
      assertThat(location.getPsiElement()).isEqualTo(barFile);
    }
    {
      String url =
          handler.testLocationUrl(label, kind, "workspace/foo/bar/bar_test", "testBar", null);
      Location<?> location = getLocation(url);
      assertThat(location).isNotNull();
      assertThat(location.getPsiElement()).isInstanceOf(JSFunction.class);
      JSFunction function = (JSFunction) location.getPsiElement();
      assertThat(function.getName()).isEqualTo("testBar");
    }
  }

  @Test
  public void testJasmineTestResolves() {
    TargetMap targetMap =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//foo/bar:foo_test_chrome-linux")
                    .setKind("js_web_test")
                    .setBuildFile(src("foo/bar/BUILD"))
                    .addDependency("//foo/bar:foo_test_wrapped_test"))
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//foo/bar:foo_test_wrapped_test")
                    .setKind("_nodejs_test")
                    .setBuildFile(src("foo/bar/BUILD"))
                    .addDependency("//foo/bar:foo_test_wrapped_test_jasmine_node_module")
                    .setJsInfo(JsIdeInfo.builder()))
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//foo/bar:foo_test_wrapped_test_jasmine_node_module")
                    .setKind("_nodejs_module")
                    .setBuildFile(src("foo/bar/BUILD"))
                    .addDependency("//foo/bar:foo_test_wrapped_test_srcs")
                    .addSource(src("foo/bar/foo_test.js"))
                    .addSource(src("foo/bar/bar_test.js"))
                    .setJsInfo(
                        JsIdeInfo.builder()
                            .addSource(src("foo/bar/foo_test.js"))
                            .addSource(src("foo/bar/bar_test.js"))))
            .build();

    registerProjectService(
        BlazeProjectDataManager.class,
        new MockBlazeProjectDataManager(
            MockBlazeProjectDataBuilder.builder(workspaceRoot)
                .setTargetMap(targetMap)
                .setWorkspaceLanguageSettings(
                    new WorkspaceLanguageSettings(
                        WorkspaceType.JAVASCRIPT, ImmutableSet.of(LanguageClass.JAVASCRIPT)))
                .build()));

    // it's usually () => {} instead of function () {}, but lambdas don't seem to work in tests

    JSFile fooFile =
        (JSFile)
            workspace.createPsiFile(
                new WorkspacePath("foo/bar/foo_test.js"),
                "describe('foo', function() {",
                "  it('should pass', function() {});",
                "});");

    JSFile barFile =
        (JSFile)
            workspace.createPsiFile(
                new WorkspacePath("foo/bar/bar_test.js"),
                "describe('bar', function() {",
                "  it('should also pass', function() {});",
                "  describe('nested', function() {",
                "    it('should be nested', function() {});",
                "    describe('super nested', function() {",
                "      it('should also be nested', function() {});",
                "    });",
                "  });",
                "});");

    workspace.createFile(
        new WorkspacePath("foo/bar/BUILD"),
        "js_web_test_suite(",
        "    name = 'foo_test',",
        "    srcs = [",
        "        'foo_test.js',",
        "        'bar_test.js',",
        "    ],",
        "    browsers = ['//testing/web/browsers:chrome-linux'],",
        ")");

    Label label = Label.create("//foo/bar:foo_test_chrome-linux");
    Kind kind = Kind.fromRuleName("js_web_test");

    {
      String url = handler.suiteLocationUrl(label, kind, "foo");
      Location<?> location = getLocation(url);
      assertThat(location).isNotNull();
      assertThat(location.getPsiElement()).isInstanceOf(JSCallExpression.class);
      JSCallExpression call = (JSCallExpression) location.getPsiElement();
      assertThat(call.getMethodExpression().getText()).isEqualTo("describe");
      assertThat(call.getContainingFile()).isEqualTo(fooFile);
    }
    {
      String url = handler.testLocationUrl(label, kind, "foo", "should pass", null);
      Location<?> location = getLocation(url);
      assertThat(location).isNotNull();
      assertThat(location.getPsiElement()).isInstanceOf(JSCallExpression.class);
      JSCallExpression call = (JSCallExpression) location.getPsiElement();
      assertThat(call.getMethodExpression().getText()).isEqualTo("it");
      assertThat(call.getContainingFile()).isEqualTo(fooFile);
    }

    JasmineFileStructure barFileStructure =
        JasmineFileStructureBuilder.getInstance().buildTestFileStructure(barFile);
    JasmineSuiteStructure barSuite = barFileStructure.findTopLevelSuiteByName("bar");
    assertThat(barSuite).isNotNull();
    JasmineSuiteStructure nestedSuite =
        BlazeJavascriptTestEventsHandlerTestUtils.findSuite(barSuite, "nested");
    assertThat(nestedSuite).isNotNull();
    JasmineSuiteStructure superNestedSuite =
        BlazeJavascriptTestEventsHandlerTestUtils.findSuite(nestedSuite, "super nested");
    assertThat(superNestedSuite).isNotNull();
    {
      String url = handler.suiteLocationUrl(label, kind, "bar");
      Location<?> location = getLocation(url);
      assertThat(location).isNotNull();
      assertThat(location.getPsiElement()).isEqualTo(barSuite.getEnclosingPsiElement());
    }
    {
      JasmineSpecStructure spec = barSuite.getInnerSpecByName("should also pass");
      assertThat(spec).isNotNull();
      String url = handler.testLocationUrl(label, kind, "bar", "bar should also pass", null);
      Location<?> location = getLocation(url);
      assertThat(location).isNotNull();
      assertThat(location.getPsiElement()).isEqualTo(spec.getEnclosingPsiElement());
    }
    {
      String url = handler.suiteLocationUrl(label, kind, "nested");
      Location<?> location = getLocation(url);
      assertThat(location).isNotNull();
      assertThat(location.getPsiElement()).isEqualTo(nestedSuite.getEnclosingPsiElement());
    }
    {
      JasmineSpecStructure spec = nestedSuite.getInnerSpecByName("should be nested");
      assertThat(spec).isNotNull();
      String url =
          handler.testLocationUrl(label, kind, "nested", "bar nested should be nested", null);
      Location<?> location = getLocation(url);
      assertThat(location).isNotNull();
      assertThat(location.getPsiElement()).isEqualTo(spec.getEnclosingPsiElement());
    }
    {
      String url = handler.suiteLocationUrl(label, kind, "super nested");
      Location<?> location = getLocation(url);
      assertThat(location).isNotNull();
      assertThat(location.getPsiElement()).isEqualTo(superNestedSuite.getEnclosingPsiElement());
    }
    {
      JasmineSpecStructure spec = superNestedSuite.getInnerSpecByName("should also be nested");
      assertThat(spec).isNotNull();
      String url =
          handler.testLocationUrl(
              label, kind, "super nested", "bar nested super nested should also be nested", null);
      Location<?> location = getLocation(url);
      assertThat(location).isNotNull();
      assertThat(location.getPsiElement()).isEqualTo(spec.getEnclosingPsiElement());
    }
  }

  @Nullable
  private Location<?> getLocation(String url) {
    String protocol = VirtualFileManager.extractProtocol(url);
    if (protocol == null) {
      return null;
    }
    String path = VirtualFileManager.extractPath(url);
    assertThat(handler.getTestLocator()).isNotNull();
    @SuppressWarnings("rawtypes")
    List<Location> locations =
        handler
            .getTestLocator()
            .getLocation(protocol, path, getProject(), GlobalSearchScope.allScope(getProject()));
    assertThat(locations).hasSize(1);
    return locations.get(0);
  }

  private static ArtifactLocation src(String relativePath) {
    return ArtifactLocation.builder().setRelativePath(relativePath).setIsSource(true).build();
  }
}
