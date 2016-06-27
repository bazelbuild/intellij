/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.lang.projectview;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.base.BlazeIntegrationTestCase;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.ExecutionRootPath;
import com.google.idea.blaze.base.sync.workspace.BlazeRoots;
import com.google.idea.blaze.base.sync.workspace.WorkingSet;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolverImpl;

/**
 * Project view file specific integration test base
 */
public abstract class ProjectViewIntegrationTestCase extends BlazeIntegrationTestCase {

  @Override
  protected void doSetup() {
    mockBlazeProjectDataManager(getMockBlazeProjectData());
  }

  private BlazeProjectData getMockBlazeProjectData() {
    BlazeRoots fakeRoots = new BlazeRoots(
      null,
      ImmutableList.of(workspaceRoot.directory()),
      new ExecutionRootPath("out/crosstool/bin"),
      new ExecutionRootPath("out/crosstool/gen")
    );
    return new BlazeProjectData(0,
                                ImmutableMap.of(),
                                fakeRoots,
                                new WorkingSet(ImmutableList.of(), ImmutableList.of(), ImmutableList.of()),
                                new WorkspacePathResolverImpl(workspaceRoot, fakeRoots),
                                null,
                                null,
                                null);
  }

}