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
package com.google.idea.blaze.android.run.testrecorder;

import static com.google.common.truth.Truth.assertThat;

import com.google.gct.testrecorder.run.TestRecorderRunConfigurationProxy;
import com.google.gct.testrecorder.ui.TestRecorderAction;
import com.google.idea.blaze.android.AndroidIntegrationTestSetupRule;
import com.google.idea.blaze.android.run.binary.BlazeAndroidBinaryRunConfigurationHandler;
import com.google.idea.blaze.android.run.binary.BlazeAndroidBinaryRunConfigurationHandlerProvider;
import com.google.idea.blaze.android.run.binary.BlazeAndroidBinaryRunConfigurationState;
import com.google.idea.blaze.base.BlazeIntegrationTestCase;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetMapBuilder;
import com.google.idea.blaze.base.model.MockBlazeProjectDataBuilder;
import com.google.idea.blaze.base.model.MockBlazeProjectDataManager;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.BlazeCommandRunConfigurationType;
import com.google.idea.blaze.base.run.confighandler.BlazeCommandRunConfigurationHandler;
import com.google.idea.blaze.base.run.confighandler.BlazeCommandRunConfigurationHandlerProvider;
import com.google.idea.blaze.base.run.confighandler.BlazeCommandRunConfigurationHandlerProvider.TargetState;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.java.AndroidBlazeRules;
import com.google.idea.sdkcompat.run.RunManagerCompat;
import com.intellij.execution.RunManagerEx;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.mock.MockModule;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.extensions.LoadingOrder;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Disposer;
import java.util.List;
import javax.annotation.Nullable;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Integration tests for {@link TestRecorderBlazeCommandRunConfiguration}. */
@RunWith(JUnit4.class)
public class TestRecorderBlazeCommandRunConfigurationTest extends BlazeIntegrationTestCase {

  @Rule
  public final AndroidIntegrationTestSetupRule androidSetupRule =
      new AndroidIntegrationTestSetupRule();

  private RunManagerImpl runManager;

  @Before
  public final void doSetup() {
    runManager = RunManagerImpl.getInstanceImpl(getProject());
    // Without BlazeProjectData, the configuration editor is always disabled.
    BlazeProjectDataManager mockProjectDataManager =
        new MockBlazeProjectDataManager(MockBlazeProjectDataBuilder.builder(workspaceRoot).build());
    registerProjectService(BlazeProjectDataManager.class, mockProjectDataManager);

    BlazeCommandRunConfigurationHandlerProvider mockHandler =
        new MockBlazeAndroidBinaryRunConfigurationHandlerProvider();
    ExtensionPoint<BlazeCommandRunConfigurationHandlerProvider> ep =
        Extensions.getRootArea()
            .getExtensionPoint(BlazeCommandRunConfigurationHandlerProvider.EP_NAME);
    ep.registerExtension(mockHandler, LoadingOrder.FIRST);
    Disposer.register(getTestRootDisposable(), () -> ep.unregisterExtension(mockHandler));

    MockBlazeProjectDataBuilder builder = MockBlazeProjectDataBuilder.builder(workspaceRoot);
    builder.setTargetMap(
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setKind(AndroidBlazeRules.RuleTypes.ANDROID_BINARY.getKind())
                    .setLabel("//label:android_binary_rule")
                    .build())
            .addTarget(
                TargetIdeInfo.builder()
                    .setKind(AndroidBlazeRules.RuleTypes.ANDROID_TEST.getKind())
                    .setLabel("//label:android_test_rule")
                    .build())
            .build());
    registerProjectService(
        BlazeProjectDataManager.class, new MockBlazeProjectDataManager(builder.build()));
  }

  @After
  public final void doTeardown() {
    runManager.clearAll();
    // We don't need to do this at setup, because it is handled by RunManagerImpl's constructor.
    // However, clearAll() clears the configuration types, so we need to reinitialize them.
    RunManagerCompat.initializeConfigurationTypes(
        runManager, ConfigurationType.CONFIGURATION_TYPE_EP);
  }

  @Test
  public void testLaunchActivityClass() {
    BlazeCommandRunConfiguration blazeConfiguration =
        BlazeCommandRunConfigurationType.getInstance()
            .getFactory()
            .createTemplateConfiguration(getProject());
    blazeConfiguration.setTarget(Label.create("//label:android_binary_rule"));
    BlazeAndroidBinaryRunConfigurationState configurationState =
        ((BlazeAndroidBinaryRunConfigurationHandler) blazeConfiguration.getHandler()).getState();
    configurationState.setMode(BlazeAndroidBinaryRunConfigurationState.LAUNCH_SPECIFIC_ACTIVITY);
    configurationState.setActivityClass("MyAppMainActivity");

    TestRecorderRunConfigurationProxy proxy =
        TestRecorderRunConfigurationProxy.getInstance(blazeConfiguration);
    assertThat(proxy).isNotNull();
    assertThat(proxy.getLaunchActivityClass()).isEqualTo("MyAppMainActivity");
  }

  @Test
  public void testSuitableRunConfigurations() {
    addConfigurations();

    List<RunConfiguration> allConfigurations =
        RunManagerEx.getInstanceEx(getProject()).getAllConfigurationsList();
    assertThat(allConfigurations.size()).isEqualTo(2);

    List<RunConfiguration> suitableConfigurations =
        TestRecorderAction.getSuitableRunConfigurations(getProject());
    assertThat(suitableConfigurations.size()).isEqualTo(1);
    assertThat(suitableConfigurations.get(0).getName()).isEqualTo("AndroidBinaryConfiguration");
  }

  private void addConfigurations() {
    BlazeCommandRunConfigurationType.BlazeCommandRunConfigurationFactory configurationFactory =
        BlazeCommandRunConfigurationType.getInstance().getFactory();

    BlazeCommandRunConfiguration blazeAndroidBinaryConfiguration =
        configurationFactory.createTemplateConfiguration(getProject());
    blazeAndroidBinaryConfiguration.setName("AndroidBinaryConfiguration");
    blazeAndroidBinaryConfiguration.setTarget(Label.create("//label:android_binary_rule"));

    BlazeCommandRunConfiguration blazeAndroidTestConfiguration =
        configurationFactory.createTemplateConfiguration(getProject());
    blazeAndroidTestConfiguration.setName("AndroidTestConfiguration");
    blazeAndroidTestConfiguration.setTarget(Label.create("//label:android_test_rule"));

    runManager.addConfiguration(
        runManager.createConfiguration(blazeAndroidBinaryConfiguration, configurationFactory),
        true);
    runManager.addConfiguration(
        runManager.createConfiguration(blazeAndroidTestConfiguration, configurationFactory), true);
  }

  // Mock out the handler to return a non-null module (required by TestRecorderAction)
  private class MockBlazeAndroidBinaryRunConfigurationHandlerProvider
      extends BlazeAndroidBinaryRunConfigurationHandlerProvider {
    @Override
    public boolean canHandleKind(TargetState state, @Nullable Kind kind) {
      return true;
    }

    @Override
    public BlazeCommandRunConfigurationHandler createHandler(BlazeCommandRunConfiguration config) {
      return new MockBlazeAndroidBinaryRunConfigurationHandler(config);
    }
  }

  private class MockBlazeAndroidBinaryRunConfigurationHandler
      extends BlazeAndroidBinaryRunConfigurationHandler {
    private final MockModule mockModule;

    MockBlazeAndroidBinaryRunConfigurationHandler(BlazeCommandRunConfiguration configuration) {
      super(configuration);
      mockModule = new MockModule(getProject(), () -> {});
    }

    @Nullable
    @Override
    public Module getModule() {
      Label label = getLabel();
      if (label != null && label.equals(Label.create("//label:android_binary_rule"))) {
        return mockModule;
      }
      return null;
    }
  }
}
