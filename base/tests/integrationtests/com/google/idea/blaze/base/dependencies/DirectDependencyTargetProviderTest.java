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
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.idea.blaze.base.BlazeIntegrationTestCase;
import com.google.idea.blaze.base.model.primitives.Label;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.PlatformTestUtil;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Tests for {@link DirectDependencyTargetProvider}. */
@RunWith(JUnit4.class)
public class DirectDependencyTargetProviderTest extends BlazeIntegrationTestCase {

  @Mock private DirectDependencyTargetProvider mockDirectDependencyTargetProvider1;
  @Mock private DirectDependencyTargetProvider mockDirectDependencyTargetProvider2;
  @Mock private DirectDependencyTargetProvider mockDirectDependencyTargetProvider3;

  @Before
  public void initTest() {
    MockitoAnnotations.initMocks(this);
    PlatformTestUtil.maskExtensions(
        DirectDependencyTargetProvider.EP_NAME,
        ImmutableList.of(
            mockDirectDependencyTargetProvider1,
            mockDirectDependencyTargetProvider2,
            mockDirectDependencyTargetProvider3),
        getTestRootDisposable());
  }

  @Test
  public void testFindDirectDependencyTargets() throws ExecutionException, InterruptedException {
    TargetInfo targetInfo1 =
        TargetInfo.builder(Label.create("//test:target1"), "proto_library").build();
    TargetInfo targetInfo2 =
        TargetInfo.builder(Label.create("//test:target2"), "proto_library").build();

    when(mockDirectDependencyTargetProvider1.getDirectDependencyTargets(
            any(Project.class), any(Label.class)))
        .thenReturn(Futures.immediateFuture(null));
    when(mockDirectDependencyTargetProvider2.getDirectDependencyTargets(
            any(Project.class), any(Label.class)))
        .thenReturn(Futures.immediateFuture(ImmutableList.of(targetInfo1, targetInfo2)));
    when(mockDirectDependencyTargetProvider3.getDirectDependencyTargets(
            any(Project.class), any(Label.class)))
        .thenReturn(Futures.immediateFuture(null));

    List<TargetInfo> actual =
        DirectDependencyTargetProvider.findDirectDependencyTargets(
                getProject(), Label.create("//test:target"))
            .get();

    assertThat(actual).containsExactly(targetInfo1, targetInfo2);
  }
}
