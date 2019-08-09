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

import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.base.BlazeIntegrationTestCase;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
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

/** Tests for {@link SourceToDependencyTargetHelper}. */
@RunWith(JUnit4.class)
public final class SourceToDependencyTargetHelperTest extends BlazeIntegrationTestCase {

  @Mock private BlazeProjectDataManager mockBlazeProjectDataManager;

  @Before
  public void initTest() {
    MockitoAnnotations.initMocks(this);
    ServiceHelper.registerProjectService(
        testFixture.getProject(),
        BlazeProjectDataManager.class,
        mockBlazeProjectDataManager,
        getTestRootDisposable());
  }

  @Test
  public void testFindTargetsBuildingSourceFile() throws ExecutionException, InterruptedException {
    Label target = Label.create("//test:target");
    String kind = "proto_library";
    String sourceRelativePath = "package/source.proto";
    BlazeProjectData blazeProjectData =
        MockBlazeProjectDataBuilder.builder()
            .setTargetMap(
                new TargetMap(
                    ImmutableMap.of(
                        TargetKey.forPlainTarget(target),
                        TargetIdeInfo.builder()
                            .setLabel(target)
                            .setKind(kind)
                            .addSource(
                                ArtifactLocation.builder().setRelativePath(sourceRelativePath))
                            .build())))
            .build();

    when(mockBlazeProjectDataManager.getBlazeProjectData()).thenReturn(blazeProjectData);

    List<TargetInfo> actual =
        SourceToDependencyTargetHelper.findTargetsBuildingSourceFile(
                testFixture.getProject(), sourceRelativePath)
            .get();

    assertThat(actual).containsExactly(TargetInfo.builder(target, kind).build());
  }
}
