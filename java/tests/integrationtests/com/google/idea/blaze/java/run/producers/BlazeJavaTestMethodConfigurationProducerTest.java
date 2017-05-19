/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.java.run.producers;

import static com.google.common.truth.Truth.assertThat;

import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetMapBuilder;
import com.google.idea.blaze.base.lang.buildfile.psi.util.PsiUtils;
import com.google.idea.blaze.base.model.MockBlazeProjectDataBuilder;
import com.google.idea.blaze.base.model.MockBlazeProjectDataManager;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.producer.BlazeRunConfigurationProducerTestCase;
import com.google.idea.blaze.base.run.state.BlazeCommandRunConfigurationCommonState;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Integration tests for {@link BlazeJavaTestMethodConfigurationProducer}. */
@RunWith(JUnit4.class)
public class BlazeJavaTestMethodConfigurationProducerTest
    extends BlazeRunConfigurationProducerTestCase {

  @Test
  public void testProducedFromPsiMethod() {
    PsiFile javaFile =
        createAndIndexFile(
            new WorkspacePath("java/com/google/test/TestClass.java"),
            "package com.google.test;",
            "@org.junit.runner.RunWith(org.junit.runners.JUnit4.class)",
            "public class TestClass {",
            "  @org.junit.Test",
            "  public void testMethod1() {}",
            "}");

    MockBlazeProjectDataBuilder builder = MockBlazeProjectDataBuilder.builder(workspaceRoot);
    builder.setTargetMap(
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setKind("java_test")
                    .setLabel("//java/com/google/test:TestClass")
                    .addSource(sourceRoot("java/com/google/test/TestClass.java"))
                    .build())
            .build());
    registerProjectService(
        BlazeProjectDataManager.class, new MockBlazeProjectDataManager(builder.build()));

    PsiMethod method = PsiUtils.findFirstChildOfClassRecursive(javaFile, PsiMethod.class);
    assertThat(method).isNotNull();

    ConfigurationContext context = createContextFromPsi(method);
    List<ConfigurationFromContext> configurations = context.getConfigurationsFromContext();
    assertThat(configurations).hasSize(1);

    ConfigurationFromContext fromContext = configurations.get(0);
    assertThat(fromContext.isProducedBy(BlazeJavaTestMethodConfigurationProducer.class)).isTrue();
    assertThat(fromContext.getConfiguration()).isInstanceOf(BlazeCommandRunConfiguration.class);

    BlazeCommandRunConfiguration config =
        (BlazeCommandRunConfiguration) fromContext.getConfiguration();
    assertThat(config.getTarget())
        .isEqualTo(TargetExpression.fromString("//java/com/google/test:TestClass"));
    assertThat(getTestFilterContents(config))
        .isEqualTo("--test_filter=com.google.test.TestClass#testMethod1$");
    assertThat(config.getName()).isEqualTo("Blaze test TestClass.testMethod1");
    assertThat(getCommandType(config)).isEqualTo(BlazeCommandName.TEST);

    BlazeCommandRunConfigurationCommonState state =
        config.getHandlerStateIfType(BlazeCommandRunConfigurationCommonState.class);
    assertThat(state.getBlazeFlagsState().getRawFlags()).contains(BlazeFlags.DISABLE_TEST_SHARDING);
  }
}
