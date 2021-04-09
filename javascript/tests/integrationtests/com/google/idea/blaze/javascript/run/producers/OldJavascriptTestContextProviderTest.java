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
import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetMapBuilder;
import com.google.idea.blaze.base.model.MockBlazeProjectDataBuilder;
import com.google.idea.blaze.base.model.MockBlazeProjectDataManager;
import com.google.idea.blaze.base.model.primitives.GenericBlazeRules.RuleTypes;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.producers.BlazeRunConfigurationProducerTestCase;
import com.google.idea.blaze.base.run.producers.PendingWebTestContext;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.psi.PsiFile;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Integration tests for {@link JavascriptTestContextProvider}.
 *
 * @deprecated jsunit_test_suites now produce jsunit_web_tests.
 */
@Deprecated
@RunWith(JUnit4.class)
public class OldJavascriptTestContextProviderTest extends BlazeRunConfigurationProducerTestCase {
  @Test
  public void testClosureTestSuite() throws Throwable {
    PsiFile jsTestFile =
        configure(
            ImmutableList.of("chrome-linux"),
            "goog.module('foo.bar.fooTest');",
            "goog.setTestOnly();",
            "const testSuite = goog.require('goog.testing.testSuite');",
            "testSuite({",
            "  testFoo() {},",
            "});");

    ConfigurationContext context = createContextFromPsi(jsTestFile);
    ConfigurationFromContext configurationFromContext = getConfigurationFromContext(context);

    BlazeCommandRunConfiguration configuration = getBlazeRunConfiguration(configurationFromContext);
    assertThat(configuration.getTargetKind()).isEqualTo(RuleTypes.WEB_TEST.getKind());
    assertThat(configuration.getTargets())
        .containsExactly(TargetExpression.fromStringSafe("//foo/bar:foo_test_chrome-linux"));
  }

  @Test
  public void testOldStyleClosureTestSuite() throws Throwable {
    createAndIndexFile(
        WorkspacePath.createIfValid("javascript/closure/testing/testsuite.js"),
        "goog.provide('goog.testing.testSuite');",
        "goog.setTestOnly('goog.testing.testSuite');",
        "goog.testing.testSuite = function(obj, opt_options) {}");

    PsiFile jsTestFile =
        configure(
            ImmutableList.of("chrome-linux"),
            "goog.require('goog.testing.testSuite');",
            "goog.testing.testSuite({",
            "  testFoo() {},",
            "});");

    ConfigurationContext context = createContextFromPsi(jsTestFile);
    ConfigurationFromContext configurationFromContext = getConfigurationFromContext(context);

    BlazeCommandRunConfiguration configuration = getBlazeRunConfiguration(configurationFromContext);
    assertThat(configuration.getTargetKind()).isEqualTo(RuleTypes.WEB_TEST.getKind());
    assertThat(configuration.getTargets())
        .containsExactly(TargetExpression.fromStringSafe("//foo/bar:foo_test_chrome-linux"));
  }

  @Test
  public void testTopLevelFunctions() throws Throwable {
    PsiFile jsTestFile = configure(ImmutableList.of("chrome-linux"), "function testFoo() {}");

    ConfigurationContext context = createContextFromPsi(jsTestFile);
    ConfigurationFromContext configurationFromContext = getConfigurationFromContext(context);

    BlazeCommandRunConfiguration configuration = getBlazeRunConfiguration(configurationFromContext);
    assertThat(configuration.getTargetKind()).isEqualTo(RuleTypes.WEB_TEST.getKind());
    assertThat(configuration.getTargets())
        .containsExactly(TargetExpression.fromStringSafe("//foo/bar:foo_test_chrome-linux"));
  }

  @Test
  public void testMultipleBrowsers() throws Throwable {
    PsiFile jsTestFile =
        configure(ImmutableList.of("chrome-linux", "firefox-linux"), "function testFoo() {}");

    ConfigurationContext context = createContextFromPsi(jsTestFile);
    ConfigurationFromContext configurationFromContext = getConfigurationFromContext(context);

    BlazeCommandRunConfiguration configuration = getBlazeRunConfiguration(configurationFromContext);

    assertThat(configuration.getPendingContext()).isNotNull();
    assertThat(configuration.getPendingContext()).isInstanceOf(PendingWebTestContext.class);
    PendingWebTestContext testContext = (PendingWebTestContext) configuration.getPendingContext();
    testContext.updateContextAndRerun(
        configuration,
        TargetInfo.builder(Label.create("//foo/bar:foo_test_firefox-linux"), "web_test").build(),
        () -> {});

    assertThat(configuration.getTargetKind()).isEqualTo(RuleTypes.WEB_TEST.getKind());
    assertThat(configuration.getTargets())
        .containsExactly(TargetExpression.fromStringSafe("//foo/bar:foo_test_firefox-linux"));
  }

  @Test
  public void testNoTests() throws Throwable {
    PsiFile jsTestFile = configure(ImmutableList.of("chrome-linux"), "function foo() {}");

    ConfigurationContext context = createContextFromPsi(jsTestFile);
    assertThat(context.getConfigurationsFromContext()).isNull();
  }

  @Test
  public void testClosureTestSuiteImportedButUnused() throws Throwable {
    PsiFile jsTestFile =
        configure(
            ImmutableList.of("chrome-linux"),
            "goog.module('foo.bar.fooTest');",
            "goog.setTestOnly();",
            "const testSuite = goog.require('goog.testing.testSuite');");

    ConfigurationContext context = createContextFromPsi(jsTestFile);
    assertThat(context.getConfigurationsFromContext()).isNull();
  }

  @Test
  public void testClosureTestSuiteImportedWrongSymbol() throws Throwable {
    PsiFile jsTestFile =
        configure(
            ImmutableList.of("chrome-linux"),
            "goog.module('foo.bar.fooTest');",
            "goog.setTestOnly();",
            "const testSuite = goog.require('my.fake.testSuite');",
            "testSuite({",
            "  testFoo() {},",
            "});");

    ConfigurationContext context = createContextFromPsi(jsTestFile);
    assertThat(context.getConfigurationsFromContext()).isNull();
  }

  private PsiFile configure(ImmutableList<String> browsers, String... filesContents)
      throws Throwable {
    TargetMapBuilder targetMapBuilder =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setKind("jsunit_test")
                    .setLabel("//foo/bar:foo_test_debug")
                    .setBuildFile(sourceRoot("foo/bar/BUILD"))
                    .addSource(sourceRoot("foo/bar/foo_test.js")));
    for (String browser : browsers) {
      targetMapBuilder.addTarget(
          TargetIdeInfo.builder()
              .setKind("web_test")
              .setLabel("//foo/bar:foo_test_" + browser)
              .setBuildFile(sourceRoot("foo/bar/BUILD"))
              .addDependency("//foo/bar:foo_test" + "_debug"));
    }
    MockBlazeProjectDataBuilder builder =
        MockBlazeProjectDataBuilder.builder(workspaceRoot).setTargetMap(targetMapBuilder.build());
    registerProjectService(
        BlazeProjectDataManager.class, new MockBlazeProjectDataManager(builder.build()));
    return createAndIndexFile(WorkspacePath.createIfValid("foo/bar/foo_test.js"), filesContents);
  }

  private static ConfigurationFromContext getConfigurationFromContext(
      ConfigurationContext context) {
    List<ConfigurationFromContext> configurations = context.getConfigurationsFromContext();
    assertThat(configurations).isNotNull();
    assertThat(configurations).hasSize(1);
    return configurations.get(0);
  }

  private static BlazeCommandRunConfiguration getBlazeRunConfiguration(
      ConfigurationFromContext configurationFromContext) {
    RunConfiguration configuration = configurationFromContext.getConfiguration();
    assertThat(configuration).isNotNull();
    assertThat(configuration).isInstanceOf(BlazeCommandRunConfiguration.class);
    return (BlazeCommandRunConfiguration) configuration;
  }
}
