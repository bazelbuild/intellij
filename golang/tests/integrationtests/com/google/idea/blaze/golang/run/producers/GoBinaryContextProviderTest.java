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
package com.google.idea.blaze.golang.run.producers;

import static com.google.common.truth.Truth.assertThat;

import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetMapBuilder;
import com.google.idea.blaze.base.model.MockBlazeProjectDataBuilder;
import com.google.idea.blaze.base.model.MockBlazeProjectDataManager;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.producers.BinaryContextRunConfigurationProducer;
import com.google.idea.blaze.base.run.producers.BlazeRunConfigurationProducerTestCase;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.psi.PsiFile;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Integration tests for {@link GoBinaryContextProvider}. */
@RunWith(JUnit4.class)
public class GoBinaryContextProviderTest extends BlazeRunConfigurationProducerTestCase {

  @Test
  public void contextProducedFromBinaryGoFileIsBinaryTarget() throws Throwable {
    PsiFile goFile =
        createAndIndexFile(new WorkspacePath("foo/bar/main.go"), "package main", "func main() {}");

    MockBlazeProjectDataBuilder builder =
        MockBlazeProjectDataBuilder.builder(workspaceRoot)
            .setTargetMap(
                TargetMapBuilder.builder()
                    .addTarget(
                        TargetIdeInfo.builder()
                            .setKind("go_binary")
                            .setLabel("//foo/bar:main")
                            .addSource(sourceRoot("foo/bar/main.go")))
                    .build());
    registerProjectService(
        BlazeProjectDataManager.class, new MockBlazeProjectDataManager(builder.build()));

    ConfigurationContext context = createContextFromPsi(goFile);
    List<ConfigurationFromContext> configurations = context.getConfigurationsFromContext();
    assertThat(configurations).isNotNull();
    assertThat(configurations).hasSize(1);

    ConfigurationFromContext fromContext = configurations.get(0);
    assertThat(fromContext.isProducedBy(BinaryContextRunConfigurationProducer.class)).isTrue();
    assertThat(fromContext.getConfiguration()).isInstanceOf(BlazeCommandRunConfiguration.class);

    BlazeCommandRunConfiguration config =
        (BlazeCommandRunConfiguration) fromContext.getConfiguration();
    assertThat(config.getTargets())
        .containsExactly(TargetExpression.fromStringSafe("//foo/bar:main"));
    assertThat(getCommandType(config)).isEqualTo(BlazeCommandName.RUN);
  }

  @Test
  public void contextProducedFromLibraryGoFileIsBinaryTarget() throws Throwable {
    PsiFile goFile =
        createAndIndexFile(new WorkspacePath("foo/bar/main.go"), "package main", "func main() {}");

    MockBlazeProjectDataBuilder builder =
        MockBlazeProjectDataBuilder.builder(workspaceRoot)
            .setTargetMap(
                TargetMapBuilder.builder()
                    .addTarget(
                        TargetIdeInfo.builder()
                            .setKind("go_library")
                            .setLabel("//foo/bar:library")
                            .addSource(sourceRoot("foo/bar/main.go")))
                    .addTarget(
                        TargetIdeInfo.builder()
                            .setKind("go_binary")
                            .setLabel("//foo/bar:main")
                            .addDependency("//foo/bar:library"))
                    .build());
    registerProjectService(
        BlazeProjectDataManager.class, new MockBlazeProjectDataManager(builder.build()));

    ConfigurationContext context = createContextFromPsi(goFile);
    List<ConfigurationFromContext> configurations = context.getConfigurationsFromContext();
    assertThat(configurations).isNotNull();
    assertThat(configurations).hasSize(1);

    ConfigurationFromContext fromContext = configurations.get(0);
    assertThat(fromContext.isProducedBy(BinaryContextRunConfigurationProducer.class)).isTrue();
    assertThat(fromContext.getConfiguration()).isInstanceOf(BlazeCommandRunConfiguration.class);

    BlazeCommandRunConfiguration config =
        (BlazeCommandRunConfiguration) fromContext.getConfiguration();
    assertThat(config.getTargets())
        .containsExactly(TargetExpression.fromStringSafe("//foo/bar:main"));
    assertThat(getCommandType(config)).isEqualTo(BlazeCommandName.RUN);
  }
}
