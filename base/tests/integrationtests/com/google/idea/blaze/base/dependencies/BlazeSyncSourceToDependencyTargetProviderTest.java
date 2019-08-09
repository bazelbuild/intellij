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
package com.google.idea.blaze.base.dependencies;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.base.BlazeIntegrationTestCase;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.MockBlazeProjectDataBuilder;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.testing.ServiceHelper;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Tests for {@link BlazeSyncSourceToDependencyTargetProvider}. */
@RunWith(JUnit4.class)
public final class BlazeSyncSourceToDependencyTargetProviderTest extends BlazeIntegrationTestCase {

  @Mock private BlazeProjectDataManager mockBlazeProjectDataManager;

  private BlazeSyncSourceToDependencyTargetProvider blazeSyncSourceToDependencyTargetProvider;

  @Before
  public void initTest() {
    MockitoAnnotations.initMocks(this);
    ServiceHelper.registerProjectService(
        testFixture.getProject(),
        BlazeProjectDataManager.class,
        mockBlazeProjectDataManager,
        getTestRootDisposable());
    blazeSyncSourceToDependencyTargetProvider = new BlazeSyncSourceToDependencyTargetProvider();
  }

  @Test
  public void testGetTargetsBuildingSourceFile() throws ExecutionException, InterruptedException {
    ImmutableList<TargetIdeInfo> targetIdeInfos =
        ImmutableList.of(
            createTargetIdeInfo(
                "//test:target1",
                "package1/source1.proto",
                "package1/source2.proto",
                "package2/source3.proto"),
            createTargetIdeInfo(
                "//test:target2",
                "package1/source4.proto",
                "package2/source5.proto",
                "package2/source6.proto"),
            createTargetIdeInfo(
                "//test:target3",
                "package1/source4.proto",
                "package2/source3.proto",
                "package2/source7.proto"));
    BlazeProjectData blazeProjectData =
        MockBlazeProjectDataBuilder.builder()
            .setTargetMap(
                new TargetMap(
                    targetIdeInfos.stream()
                        .collect(
                            ImmutableMap.toImmutableMap(
                                targetIdeInfo -> targetIdeInfo.getKey(),
                                targetIdeInfo -> targetIdeInfo))))
            .build();

    when(mockBlazeProjectDataManager.getBlazeProjectData()).thenReturn(blazeProjectData);

    List<TargetInfo> actual =
        blazeSyncSourceToDependencyTargetProvider
            .getTargetsBuildingSourceFile(testFixture.getProject(), "package2/source3.proto")
            .get();

    assertThat(actual)
        .containsExactly(
            TargetInfo.builder(Label.create("//test:target1"), "proto_library").build(),
            TargetInfo.builder(Label.create("//test:target3"), "proto_library").build());
  }

  private static TargetIdeInfo createTargetIdeInfo(String label, String... sourceRelativePaths) {
    TargetIdeInfo.Builder targetIdeInfo =
        TargetIdeInfo.builder().setLabel(label).setKind("proto_library");
    for (String sourceRelativePath : sourceRelativePaths) {
      targetIdeInfo.addSource(ArtifactLocation.builder().setRelativePath(sourceRelativePath));
    }
    return targetIdeInfo.build();
  }
}
