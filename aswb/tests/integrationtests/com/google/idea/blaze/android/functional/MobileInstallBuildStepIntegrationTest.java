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
package com.google.idea.blaze.android.functional;

import static com.google.common.truth.Truth.assertThat;
import static com.google.idea.blaze.android.targetmapbuilder.NbAndroidTarget.android_binary;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.ddmlib.IDevice;
import com.android.ddmlib.IDevice.HardwareFeature;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.devices.Abi;
import com.android.tools.idea.run.AndroidDevice;
import com.android.tools.idea.run.DeviceFutures;
import com.android.tools.idea.run.LaunchCompatibility;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.build.lib.rules.android.deployinfo.AndroidDeployInfoOuterClass.AndroidDeployInfo;
import com.google.errorprone.annotations.Keep;
import com.google.idea.blaze.android.BlazeAndroidIntegrationTestCase;
import com.google.idea.blaze.android.MessageCollector;
import com.google.idea.blaze.android.MockSdkUtil;
import com.google.idea.blaze.android.run.binary.mobileinstall.AdbTunnelConfigurator;
import com.google.idea.blaze.android.run.binary.mobileinstall.AdbTunnelConfigurator.AdbTunnelConfiguratorProvider;
import com.google.idea.blaze.android.run.binary.mobileinstall.MobileInstallBuildStep;
import com.google.idea.blaze.android.run.deployinfo.BlazeAndroidDeployInfo;
import com.google.idea.blaze.android.run.deployinfo.BlazeApkDeployInfoProtoHelper;
import com.google.idea.blaze.android.run.deployinfo.BlazeApkDeployInfoProtoHelper.GetDeployInfoException;
import com.google.idea.blaze.android.run.runner.BlazeAndroidDeviceSelector.DeviceSession;
import com.google.idea.blaze.base.async.process.ExternalTask;
import com.google.idea.blaze.base.async.process.ExternalTaskProvider;
import com.google.idea.blaze.base.bazel.BuildSystemProvider;
import com.google.idea.blaze.base.bazel.BuildSystemProviderWrapper;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper.GetArtifactsException;
import com.google.idea.blaze.base.command.buildresult.ParsedBepOutput;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.sync.aspects.BuildResult;
import com.intellij.openapi.project.Project;
import com.intellij.ui.SimpleColoredComponent;
import java.io.File;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.Nullable;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Integration tests for {@link MobileInstallBuildStep} */
@RunWith(JUnit4.class)
public class MobileInstallBuildStepIntegrationTest extends BlazeAndroidIntegrationTestCase {
  /** Exposed to test methods to toggle presence of execroot */
  private BuildResultHelper mockBuildResultHelper;

  private Label buildTarget;
  private ImmutableList<String> blazeFlags;
  private ImmutableList<String> execFlags;
  private ExternalTaskInterceptor externalTaskInterceptor;
  private BlazeContext context;
  private MessageCollector messageCollector;

  @Before
  public void setupProject() {
    setProjectView(
        "directories:",
        "  java/com/foo/app",
        "targets:",
        "  //java/com/foo/app:app",
        "android_sdk_platform: android-27");
    MockSdkUtil.registerSdk(workspace, "27");

    workspace.createFile(
        new WorkspacePath("java/com/foo/app/MainActivity.java"),
        "package com.foo.app",
        "import android.app.Activity;",
        "public class MainActivity extends Activity {}");

    setTargetMap(android_binary("//java/com/foo/app:app").src("MainActivity.java"));
    runFullBlazeSyncWithNoIssues();

    // MI invocation flags
    buildTarget = Label.create("//java/com/foo/app:app");
    blazeFlags = ImmutableList.of("some_blaze_flag", "other_blaze_flag");
    execFlags = ImmutableList.of("some_exec_flag", "other_exec_flag");
  }

  /** Setup build result helper to return BEP output with test execroot by default. */
  @Before
  public void setupBuildResultHelperProvider() throws GetArtifactsException {
    mockBuildResultHelper = mock(BuildResultHelper.class);
    when(mockBuildResultHelper.getBuildOutput())
        .thenReturn(
            new ParsedBepOutput(null, getExecRoot(), null, null, 0, BuildResult.SUCCESS, 0));
    BuildSystemProviderWrapper buildSystem = new BuildSystemProviderWrapper(() -> getProject());
    buildSystem.setBuildResultHelperSupplier(() -> mockBuildResultHelper);
    registerExtension(BuildSystemProvider.EP_NAME, buildSystem);
  }

  @Before
  public void setupTestDetailCollectors() {
    // Setup interceptor for fake running of blaze commands and capture details.
    externalTaskInterceptor = new ExternalTaskInterceptor();
    registerApplicationService(ExternalTaskProvider.class, externalTaskInterceptor);

    // Collect messages sent to IssueOutput.
    messageCollector = new MessageCollector();
    context = BlazeContext.create();
    context.addOutputSink(IssueOutput.class, messageCollector);
  }

  @Test
  public void deployInfoBuiltCorrectly() throws Exception {
    // Mobile-install build step requires only one device be active.  DeviceFutures class is final,
    // so we have to make one with a stub AndroidDevice.
    DeviceFutures deviceFutures = new DeviceFutures(ImmutableList.of(new FakeDevice()));

    // Return fake deploy info proto and mocked deploy info data object.
    AndroidDeployInfo fakeProto = AndroidDeployInfo.newBuilder().build();
    BlazeAndroidDeployInfo mockDeployInfo = mock(BlazeAndroidDeployInfo.class);
    BlazeApkDeployInfoProtoHelper helper = mock(BlazeApkDeployInfoProtoHelper.class);
    when(helper.readDeployInfoProtoForTarget(eq(buildTarget), any(BuildResultHelper.class), any()))
        .thenReturn(fakeProto);
    when(helper.extractDeployInfoAndInvalidateManifests(
            getProject(), new File(getExecRoot()), fakeProto))
        .thenReturn(mockDeployInfo);

    // Perform
    MobileInstallBuildStep buildStep =
        new MobileInstallBuildStep(getProject(), buildTarget, blazeFlags, execFlags, helper);
    buildStep.build(context, new DeviceSession(null, deviceFutures, null));

    // Verify
    assertThat(buildStep.getDeployInfo()).isNotNull();
    assertThat(buildStep.getDeployInfo()).isEqualTo(mockDeployInfo);
    assertThat(externalTaskInterceptor.context).isEqualTo(context);
    assertThat(externalTaskInterceptor.command).containsAllIn(blazeFlags);
    assertThat(externalTaskInterceptor.command).containsAllIn(execFlags);
    assertThat(externalTaskInterceptor.command)
        .containsAnyOf("serial-number", "serial-number:tcp:0");
    assertThat(externalTaskInterceptor.command).contains(buildTarget.toString());
  }

  @Test
  public void deployInfoBuiltCorrectly_withInactiveAdbTunnelSetup() throws Exception {
    // Mobile-install build step requires only one device be active.  DeviceFutures class is final,
    // so we have to make one with a stub AndroidDevice.
    DeviceFutures deviceFutures = new DeviceFutures(ImmutableList.of(new FakeDevice()));

    // Return fake deploy info proto and mocked deploy info data object.
    AndroidDeployInfo fakeProto = AndroidDeployInfo.newBuilder().build();
    BlazeAndroidDeployInfo mockDeployInfo = mock(BlazeAndroidDeployInfo.class);
    BlazeApkDeployInfoProtoHelper helper = mock(BlazeApkDeployInfoProtoHelper.class);
    when(helper.readDeployInfoProtoForTarget(eq(buildTarget), any(BuildResultHelper.class), any()))
        .thenReturn(fakeProto);
    when(helper.extractDeployInfoAndInvalidateManifests(
            getProject(), new File(getExecRoot()), fakeProto))
        .thenReturn(mockDeployInfo);

    // Setup mock AdbTunnelConfigurator for testing device port flags.
    AdbTunnelConfigurator tunnelConfigurator = mock(AdbTunnelConfigurator.class);
    when(tunnelConfigurator.isActive()).thenReturn(false);
    when(tunnelConfigurator.getAdbServerPort()).thenReturn(12345);
    registerExtension(AdbTunnelConfiguratorProvider.EP_NAME, providerCxt -> tunnelConfigurator);

    // Perform
    MobileInstallBuildStep buildStep =
        new MobileInstallBuildStep(getProject(), buildTarget, blazeFlags, execFlags, helper);
    buildStep.build(context, new DeviceSession(null, deviceFutures, null));

    // Verify
    assertThat(buildStep.getDeployInfo()).isNotNull();
    assertThat(buildStep.getDeployInfo()).isEqualTo(mockDeployInfo);
    assertThat(externalTaskInterceptor.context).isEqualTo(context);
    assertThat(externalTaskInterceptor.command).containsAllIn(blazeFlags);
    assertThat(externalTaskInterceptor.command).containsAllIn(execFlags);
    assertThat(externalTaskInterceptor.command).contains("--device");
    // workaround for inconsistent stateful AndroidDebugBridge class.
    assertThat(externalTaskInterceptor.command)
        .containsAnyOf("serial-number", "serial-number:tcp:0");
    assertThat(externalTaskInterceptor.command).contains(buildTarget.toString());
  }

  @Test
  public void deployInfoBuiltCorrectly_withAdbTunnelSetup() throws Exception {
    // Mobile-install build step requires only one device be active.  DeviceFutures class is final,
    // so we have to make one with a stub AndroidDevice.
    DeviceFutures deviceFutures = new DeviceFutures(ImmutableList.of(new FakeDevice()));

    // Return fake deploy info proto and mocked deploy info data object.
    AndroidDeployInfo fakeProto = AndroidDeployInfo.newBuilder().build();
    BlazeAndroidDeployInfo mockDeployInfo = mock(BlazeAndroidDeployInfo.class);
    BlazeApkDeployInfoProtoHelper helper = mock(BlazeApkDeployInfoProtoHelper.class);
    when(helper.readDeployInfoProtoForTarget(eq(buildTarget), any(BuildResultHelper.class), any()))
        .thenReturn(fakeProto);
    when(helper.extractDeployInfoAndInvalidateManifests(
            getProject(), new File(getExecRoot()), fakeProto))
        .thenReturn(mockDeployInfo);

    // Setup mock AdbTunnelConfigurator for testing device port flags.
    AdbTunnelConfigurator tunnelConfigurator = mock(AdbTunnelConfigurator.class);
    when(tunnelConfigurator.isActive()).thenReturn(true);
    when(tunnelConfigurator.getAdbServerPort()).thenReturn(12345);
    registerExtension(AdbTunnelConfiguratorProvider.EP_NAME, providerCxt -> tunnelConfigurator);

    // Perform
    MobileInstallBuildStep buildStep =
        new MobileInstallBuildStep(getProject(), buildTarget, blazeFlags, execFlags, helper);
    buildStep.build(context, new DeviceSession(null, deviceFutures, null));

    // Verify
    assertThat(buildStep.getDeployInfo()).isNotNull();
    assertThat(buildStep.getDeployInfo()).isEqualTo(mockDeployInfo);
    assertThat(externalTaskInterceptor.context).isEqualTo(context);
    assertThat(externalTaskInterceptor.command).containsAllIn(blazeFlags);
    assertThat(externalTaskInterceptor.command).containsAllIn(execFlags);
    assertThat(externalTaskInterceptor.command).contains("--device");
    assertThat(externalTaskInterceptor.command).contains("serial-number:tcp:12345");
    assertThat(externalTaskInterceptor.command).contains(buildTarget.toString());
  }

  @Test
  public void deployInfoBuiltCorrectly_withNullAdbTunnelSetup() throws Exception {
    // Mobile-install build step requires only one device be active.  DeviceFutures class is final,
    // so we have to make one with a stub AndroidDevice.
    DeviceFutures deviceFutures = new DeviceFutures(ImmutableList.of(new FakeDevice()));

    // Return fake deploy info proto and mocked deploy info data object.
    AndroidDeployInfo fakeProto = AndroidDeployInfo.newBuilder().build();
    BlazeAndroidDeployInfo mockDeployInfo = mock(BlazeAndroidDeployInfo.class);
    BlazeApkDeployInfoProtoHelper helper = mock(BlazeApkDeployInfoProtoHelper.class);
    when(helper.readDeployInfoProtoForTarget(eq(buildTarget), any(BuildResultHelper.class), any()))
        .thenReturn(fakeProto);
    when(helper.extractDeployInfoAndInvalidateManifests(
            getProject(), new File(getExecRoot()), fakeProto))
        .thenReturn(mockDeployInfo);

    // Setup mock AdbTunnelConfigurator for testing device port flags.
    AdbTunnelConfigurator tunnelConfigurator = mock(AdbTunnelConfigurator.class);
    when(tunnelConfigurator.isActive()).thenReturn(true);
    when(tunnelConfigurator.getAdbServerPort()).thenReturn(12345);
    registerExtension(AdbTunnelConfiguratorProvider.EP_NAME, providerCxt -> null);

    // Perform
    MobileInstallBuildStep buildStep =
        new MobileInstallBuildStep(getProject(), buildTarget, blazeFlags, execFlags, helper);
    buildStep.build(context, new DeviceSession(null, deviceFutures, null));

    // Verify
    assertThat(buildStep.getDeployInfo()).isNotNull();
    assertThat(buildStep.getDeployInfo()).isEqualTo(mockDeployInfo);
    assertThat(externalTaskInterceptor.context).isEqualTo(context);
    assertThat(externalTaskInterceptor.command).containsAllIn(blazeFlags);
    assertThat(externalTaskInterceptor.command).containsAllIn(execFlags);
    assertThat(externalTaskInterceptor.command).contains("--device");
    // workaround for inconsistent stateful AndroidDebugBridge class.
    assertThat(externalTaskInterceptor.command)
        .containsAnyOf("serial-number", "serial-number:tcp:0");
    assertThat(externalTaskInterceptor.command).contains(buildTarget.toString());
  }

  @Test
  public void moreThanOneDevice() throws Exception {
    // Make blaze command invocation always pass.
    registerApplicationService(ExternalTaskProvider.class, builder -> scopes -> 0);

    // Mobile-install build step requires only one device be active.  DeviceFutures class is final,
    // so we have to make one with a stub AndroidDevice.
    DeviceFutures deviceFutures =
        new DeviceFutures(ImmutableList.of(new FakeDevice(), new FakeDevice()));

    // Return fake deploy info proto and mocked deploy info data object.
    AndroidDeployInfo fakeProto = AndroidDeployInfo.newBuilder().build();
    BlazeAndroidDeployInfo mockDeployInfo = mock(BlazeAndroidDeployInfo.class);
    BlazeApkDeployInfoProtoHelper helper = mock(BlazeApkDeployInfoProtoHelper.class);
    when(helper.readDeployInfoProtoForTarget(eq(buildTarget), any(BuildResultHelper.class), any()))
        .thenReturn(fakeProto);
    when(helper.extractDeployInfoAndInvalidateManifests(
            getProject(), new File(getExecRoot()), fakeProto))
        .thenReturn(mockDeployInfo);

    // Perform
    MobileInstallBuildStep buildStep =
        new MobileInstallBuildStep(
            getProject(), buildTarget, ImmutableList.of(), ImmutableList.of(), helper);
    buildStep.build(context, new DeviceSession(null, deviceFutures, null));

    // Verify
    assertThat(context.hasErrors()).isTrue();
    assertThat(messageCollector.getMessages())
        .contains("Only one device can be used with mobile-install.");
  }

  @Test
  public void exceptionDuringDeployInfoExtraction() throws Exception {
    // Make blaze command invocation always pass.
    registerApplicationService(ExternalTaskProvider.class, builder -> scopes -> 0);

    // Mobile-install build step requires only one device be active.  DeviceFutures class is final,
    // so we have to make one with a stub AndroidDevice.
    DeviceFutures deviceFutures = new DeviceFutures(ImmutableList.of(new FakeDevice()));

    // Return fake deploy info proto and mocked deploy info data object.
    AndroidDeployInfo fakeProto = AndroidDeployInfo.newBuilder().build();
    BlazeApkDeployInfoProtoHelper helper = mock(BlazeApkDeployInfoProtoHelper.class);
    when(helper.readDeployInfoProtoForTarget(eq(buildTarget), any(BuildResultHelper.class), any()))
        .thenReturn(fakeProto);
    when(helper.extractDeployInfoAndInvalidateManifests(any(), any(), any()))
        .thenThrow(new GetDeployInfoException("Fake Exception"));

    // Perform
    MobileInstallBuildStep buildStep =
        new MobileInstallBuildStep(
            getProject(), buildTarget, ImmutableList.of(), ImmutableList.of(), helper);
    buildStep.build(context, new DeviceSession(null, deviceFutures, null));

    // Verify
    assertThat(context.hasErrors()).isTrue();
    assertThat(messageCollector.getMessages())
        .contains("Could not read apk deploy info from build: Fake Exception");
  }

  @Test
  public void blazeCommandFailed() throws Exception {
    // Return a non-zero value to indicate blaze command run failure.
    registerApplicationService(ExternalTaskProvider.class, builder -> scopes -> 1337);

    // Mobile-install build step requires only one device be active.  DeviceFutures class is final,
    // so we have to make one with a stub AndroidDevice.
    DeviceFutures deviceFutures = new DeviceFutures(ImmutableList.of(new FakeDevice()));

    // Return fake deploy info proto and mocked deploy info data object.
    AndroidDeployInfo fakeProto = AndroidDeployInfo.newBuilder().build();
    BlazeAndroidDeployInfo mockDeployInfo = mock(BlazeAndroidDeployInfo.class);
    BlazeApkDeployInfoProtoHelper helper = mock(BlazeApkDeployInfoProtoHelper.class);
    when(helper.readDeployInfoProtoForTarget(eq(buildTarget), any(BuildResultHelper.class), any()))
        .thenReturn(fakeProto);
    when(helper.extractDeployInfoAndInvalidateManifests(
            getProject(), new File(getExecRoot()), fakeProto))
        .thenReturn(mockDeployInfo);

    // Perform
    MobileInstallBuildStep buildStep =
        new MobileInstallBuildStep(
            getProject(), buildTarget, ImmutableList.of(), ImmutableList.of(), helper);
    buildStep.build(context, new DeviceSession(null, deviceFutures, null));

    // Verify
    assertThat(context.hasErrors()).isTrue();
    assertThat(messageCollector.getMessages())
        .contains("Blaze build failed. See Blaze Console for details.");
  }

  @Test
  public void nullExecRoot() throws Exception {
    // Return null execroot
    when(mockBuildResultHelper.getBuildOutput())
        .thenReturn(new ParsedBepOutput(null, null, null, null, 0, BuildResult.SUCCESS, 0));

    // Mobile-install build step requires only one device be active.  DeviceFutures class is final,
    // so we have to make one with a stub AndroidDevice.
    DeviceFutures deviceFutures = new DeviceFutures(ImmutableList.of(new FakeDevice()));

    // Return fake deploy info proto and mocked deploy info data object.
    AndroidDeployInfo fakeProto = AndroidDeployInfo.newBuilder().build();
    BlazeAndroidDeployInfo mockDeployInfo = mock(BlazeAndroidDeployInfo.class);
    BlazeApkDeployInfoProtoHelper helper = mock(BlazeApkDeployInfoProtoHelper.class);
    when(helper.readDeployInfoProtoForTarget(eq(buildTarget), any(BuildResultHelper.class), any()))
        .thenReturn(fakeProto);
    when(helper.extractDeployInfoAndInvalidateManifests(
            getProject(), new File(getExecRoot()), fakeProto))
        .thenReturn(mockDeployInfo);

    // Perform
    MobileInstallBuildStep buildStep =
        new MobileInstallBuildStep(getProject(), buildTarget, blazeFlags, execFlags, helper);
    buildStep.build(context, new DeviceSession(null, deviceFutures, null));

    // Verify
    assertThat(context.hasErrors()).isTrue();
    assertThat(messageCollector.getMessages()).contains("Could not locate execroot!");
  }

  /** Saves the latest blaze command and context for later verification. */
  private static class ExternalTaskInterceptor implements ExternalTaskProvider {
    ImmutableList<String> command;
    BlazeContext context;

    @Override
    public ExternalTask build(ExternalTask.Builder builder) {
      command = builder.command.build();
      context = builder.context;
      return scopes -> 0;
    }
  }

  /**
   * A fake android device that returns a mocked launched-device. This class is required because
   * {@link DeviceFutures} and all other implementations of {@link AndroidDevice} are final,
   * therefore we need this to stub out a fake {@link DeviceSession}.
   */
  private static class FakeDevice extends AndroidDeviceCompat {
    @Override
    public ListenableFuture<IDevice> getLaunchedDevice() {
      IDevice device = mock(IDevice.class);
      when(device.getSerialNumber()).thenReturn("serial-number");
      return Futures.immediateFuture(device);
    }

    //
    // All methods below this point has no purpose. Please ignore.
    //
    @Override
    public boolean isRunning() {
      return false;
    }

    @Override
    public boolean isVirtual() {
      return false;
    }

    @Override
    public com.android.sdklib.AndroidVersion getVersion() {
      return null;
    }

    @Override
    public int getDensity() {
      return 0;
    }

    @Override
    public List<Abi> getAbis() {
      return null;
    }

    @Override
    public String getSerial() {
      return null;
    }

    @Override
    public boolean supportsFeature(HardwareFeature hardwareFeature) {
      return false;
    }

    @Override
    public String getName() {
      return null;
    }

    // @Override #api42
    @Keep
    public boolean renderLabel(
        SimpleColoredComponent simpleColoredComponent, boolean b, @Nullable String s) {
      return false;
    }

    // @Override #api42
    @Keep
    public void prepareToRenderLabel() {}

    // api40: see new API for canRun below
    @Keep
    public LaunchCompatibility canRun(
        com.android.sdklib.AndroidVersion androidVersion,
        IAndroidTarget iAndroidTarget,
        EnumSet<HardwareFeature> enumSet,
        @Nullable Set<String> set) {
      return null;
    }

    @Override
    public ListenableFuture<IDevice> launch(Project project) {
      return null;
    }

    // @Override #api 3.6
    @Keep
    public ListenableFuture<IDevice> launch(Project project, String s) {
      return null;
    }

    // @Override #api 4.0
    @Keep
    public ListenableFuture<IDevice> launch(Project project, List<String> list) {
      return null;
    }

    // @Override #api 3.6
    @Keep
    public boolean isDebuggable() {
      return false;
    }
  }
}
