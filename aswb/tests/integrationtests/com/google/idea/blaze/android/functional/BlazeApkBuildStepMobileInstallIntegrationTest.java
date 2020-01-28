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
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.ddmlib.IDevice;
import com.android.ddmlib.IDevice.HardwareFeature;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.devices.Abi;
import com.android.tools.idea.run.AndroidDevice;
import com.android.tools.idea.run.ApkProvisionException;
import com.android.tools.idea.run.DeviceFutures;
import com.android.tools.idea.run.LaunchCompatibility;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.build.lib.rules.android.deployinfo.AndroidDeployInfoOuterClass.AndroidDeployInfo;
import com.google.idea.blaze.android.BlazeAndroidIntegrationTestCase;
import com.google.idea.blaze.android.MessageCollector;
import com.google.idea.blaze.android.MockSdkUtil;
import com.google.idea.blaze.android.run.binary.mobileinstall.BlazeApkBuildStepMobileInstall;
import com.google.idea.blaze.android.run.deployinfo.BlazeAndroidDeployInfo;
import com.google.idea.blaze.android.run.deployinfo.BlazeApkDeployInfoProtoHelper;
import com.google.idea.blaze.android.run.deployinfo.BlazeApkDeployInfoProtoHelper.GetDeployInfoException;
import com.google.idea.blaze.android.run.runner.BlazeAndroidDeviceSelector.DeviceSession;
import com.google.idea.blaze.base.async.process.ExternalTask;
import com.google.idea.blaze.base.async.process.ExternalTaskProvider;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper.GetArtifactsException;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelperProvider;
import com.google.idea.blaze.base.command.buildresult.ParsedBepOutput;
import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.intellij.openapi.project.Project;
import com.intellij.ui.SimpleColoredComponent;
import java.io.File;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jetbrains.annotations.Nullable;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Integration tests for {@link BlazeApkBuildStepMobileInstall} */
@RunWith(JUnit4.class)
public class BlazeApkBuildStepMobileInstallIntegrationTest extends BlazeAndroidIntegrationTestCase {
  /** Exposed to test methods to toggle presence of execroot */
  private BuildResultHelper mockBuildResultHelper;

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
    runFullBlazeSync();
  }

  /** Setup build result helper to return BEP output with test execroot by default. */
  @Before
  public void setupBuildResultHelperProvider() throws GetArtifactsException {
    mockBuildResultHelper = mock(BuildResultHelper.class);
    when(mockBuildResultHelper.getBuildOutput())
        .thenReturn(new ParsedBepOutput(null, getExecRoot(), null, null, 0));
    registerExtension(
        BuildResultHelperProvider.EP_NAME,
        new BuildResultHelperProvider() {
          @Override
          public Optional<BuildResultHelper> doCreate(Project project) {
            return Optional.of(mockBuildResultHelper);
          }

          @Override
          public Optional<BuildResultHelper> doCreateForSync(Project project, BlazeInfo blazeInfo) {
            return Optional.empty();
          }
        });
  }

  @Test
  public void deployInfoBuiltCorrectly()
      throws GetDeployInfoException, ApkProvisionException, GetArtifactsException {
    Label buildTarget = Label.create("//java/com/foo/app:app");
    BlazeContext context = new BlazeContext();
    ImmutableList<String> blazeFlags = ImmutableList.of("some_blaze_flag", "other_blaze_flag");
    ImmutableList<String> execFlags = ImmutableList.of("some_exec_flag", "other_exec_flag");

    // Setup interceptor for fake running of blaze commands and capture details.
    ExternalTaskInterceptor externalTaskInterceptor = new ExternalTaskInterceptor();
    registerApplicationService(ExternalTaskProvider.class, externalTaskInterceptor);

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
            eq(getProject()), eq(new File(getExecRoot())), eq(fakeProto)))
        .thenReturn(mockDeployInfo);

    // Perform
    BlazeApkBuildStepMobileInstall buildStep =
        new BlazeApkBuildStepMobileInstall(
            getProject(), buildTarget, blazeFlags, execFlags, helper);
    buildStep.build(context, new DeviceSession(null, deviceFutures, null));

    // Verify
    assertThat(buildStep.getDeployInfo()).isNotNull();
    assertThat(buildStep.getDeployInfo()).isEqualTo(mockDeployInfo);
    assertThat(externalTaskInterceptor.context).isEqualTo(context);
    assertThat(externalTaskInterceptor.command).containsAllIn(blazeFlags);
    assertThat(externalTaskInterceptor.command).containsAllIn(execFlags);
    assertThat(externalTaskInterceptor.command).contains(buildTarget.toString());
    // Note: Invoking mobile-install does not require adding android_deploy_info output group.
  }

  @Test
  public void moreThanOneDevice() throws GetDeployInfoException, GetArtifactsException {
    Label buildTarget = Label.create("//java/com/foo/app:app");

    MessageCollector messageCollector = new MessageCollector();
    BlazeContext context = new BlazeContext();
    context.addOutputSink(IssueOutput.class, messageCollector);

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
            eq(getProject()), eq(new File(getExecRoot())), eq(fakeProto)))
        .thenReturn(mockDeployInfo);

    // Perform
    BlazeApkBuildStepMobileInstall buildStep =
        new BlazeApkBuildStepMobileInstall(
            getProject(), buildTarget, ImmutableList.of(), ImmutableList.of(), helper);
    buildStep.build(context, new DeviceSession(null, deviceFutures, null));

    // Verify
    assertThat(context.hasErrors()).isTrue();
    assertThat(messageCollector.getMessages())
        .contains("Only one device can be used with mobile-install.");
  }

  @Test
  public void exceptionDuringDeployInfoExtraction()
      throws GetDeployInfoException, GetArtifactsException {
    Label buildTarget = Label.create("//java/com/foo/app:app");

    MessageCollector messageCollector = new MessageCollector();
    BlazeContext context = new BlazeContext();
    context.addOutputSink(IssueOutput.class, messageCollector);

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
    BlazeApkBuildStepMobileInstall buildStep =
        new BlazeApkBuildStepMobileInstall(
            getProject(), buildTarget, ImmutableList.of(), ImmutableList.of(), helper);
    buildStep.build(context, new DeviceSession(null, deviceFutures, null));

    // Verify
    assertThat(context.hasErrors()).isTrue();
    assertThat(messageCollector.getMessages())
        .contains("Could not read apk deploy info from build: Fake Exception");
  }

  @Test
  public void blazeCommandFailed() throws GetDeployInfoException {
    Label buildTarget = Label.create("//java/com/foo/app:app");

    MessageCollector messageCollector = new MessageCollector();
    BlazeContext context = new BlazeContext();
    context.addOutputSink(IssueOutput.class, messageCollector);

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
            eq(getProject()), eq(new File(getExecRoot())), eq(fakeProto)))
        .thenReturn(mockDeployInfo);

    // Perform
    BlazeApkBuildStepMobileInstall buildStep =
        new BlazeApkBuildStepMobileInstall(
            getProject(), buildTarget, ImmutableList.of(), ImmutableList.of(), helper);
    buildStep.build(context, new DeviceSession(null, deviceFutures, null));

    // Verify
    assertThat(context.hasErrors()).isTrue();
    assertThat(messageCollector.getMessages())
        .contains("Blaze build failed. See Blaze Console for details.");
  }

  @Test
  public void nullExecRoot() throws GetDeployInfoException, GetArtifactsException {
    Label buildTarget = Label.create("//java/com/foo/app:app");
    ImmutableList<String> blazeFlags = ImmutableList.of("some_blaze_flag", "other_blaze_flag");
    ImmutableList<String> execFlags = ImmutableList.of("some_exec_flag", "other_exec_flag");

    MessageCollector messageCollector = new MessageCollector();
    BlazeContext context = new BlazeContext();
    context.addOutputSink(IssueOutput.class, messageCollector);

    // Return null execroot
    when(mockBuildResultHelper.getBuildOutput())
        .thenReturn(new ParsedBepOutput(null, null, null, null, 0));

    // Setup interceptor for fake running of blaze commands and capture details.
    ExternalTaskInterceptor externalTaskInterceptor = new ExternalTaskInterceptor();
    registerApplicationService(ExternalTaskProvider.class, externalTaskInterceptor);

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
            eq(getProject()), eq(new File(getExecRoot())), eq(fakeProto)))
        .thenReturn(mockDeployInfo);

    // Perform
    BlazeApkBuildStepMobileInstall buildStep =
        new BlazeApkBuildStepMobileInstall(
            getProject(), buildTarget, blazeFlags, execFlags, helper);
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
  private static class FakeDevice implements AndroidDevice {
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

    @Override
    public boolean renderLabel(
        SimpleColoredComponent simpleColoredComponent, boolean b, @Nullable String s) {
      return false;
    }

    @Override
    public void prepareToRenderLabel() {}

    @Override
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
    public ListenableFuture<IDevice> launch(Project project, String s) {
      return null;
    }

    // @Override #api 4.0
    public ListenableFuture<IDevice> launch(Project project, List<String> list) {
      return null;
    }

    // @Override #api 3.6
    public boolean isDebuggable() {
      return false;
    }
  }
}
