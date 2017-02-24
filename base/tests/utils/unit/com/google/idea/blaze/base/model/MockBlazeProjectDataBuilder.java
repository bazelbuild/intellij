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
package com.google.idea.blaze.base.model;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.model.primitives.ExecutionRootPath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.model.primitives.WorkspaceType;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoderImpl;
import com.google.idea.blaze.base.sync.workspace.BlazeRoots;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolverImpl;
import java.io.File;

/**
 * Use to build mock project data for tests.
 *
 * <p>For any data you don't supply, the builder makes a best-effort attempt to create default
 * objects using whatever data you have supplied if applicable.
 */
public class MockBlazeProjectDataBuilder {
  private final WorkspaceRoot workspaceRoot;

  private long syncTime = 0;
  private TargetMap targetMap;
  private ImmutableMap<String, String> blazeInfo;
  private BlazeRoots blazeRoots;
  private BlazeVersionData blazeVersionData;
  private WorkspacePathResolver workspacePathResolver;
  private ArtifactLocationDecoder artifactLocationDecoder;
  private WorkspaceLanguageSettings workspaceLanguageSettings;
  private SyncState syncState;
  private ImmutableMultimap<TargetKey, TargetKey> reverseDependencies;

  private MockBlazeProjectDataBuilder(WorkspaceRoot workspaceRoot) {
    this.workspaceRoot = workspaceRoot;
  }

  public static MockBlazeProjectDataBuilder builder(WorkspaceRoot workspaceRoot) {
    return new MockBlazeProjectDataBuilder(workspaceRoot);
  }

  public MockBlazeProjectDataBuilder setSyncTime(long syncTime) {
    this.syncTime = syncTime;
    return this;
  }

  public MockBlazeProjectDataBuilder setTargetMap(TargetMap targetMap) {
    this.targetMap = targetMap;
    return this;
  }

  public MockBlazeProjectDataBuilder setBlazeInfo(ImmutableMap<String, String> blazeInfo) {
    this.blazeInfo = blazeInfo;
    return this;
  }

  public MockBlazeProjectDataBuilder setBlazeRoots(BlazeRoots blazeRoots) {
    this.blazeRoots = blazeRoots;
    return this;
  }

  public MockBlazeProjectDataBuilder setBlazeVersionData(BlazeVersionData blazeVersionData) {
    this.blazeVersionData = blazeVersionData;
    return this;
  }

  public MockBlazeProjectDataBuilder setWorkspacePathResolver(
      WorkspacePathResolver workspacePathResolver) {
    this.workspacePathResolver = workspacePathResolver;
    return this;
  }

  public MockBlazeProjectDataBuilder setArtifactLocationDecoder(
      ArtifactLocationDecoder artifactLocationDecoder) {
    this.artifactLocationDecoder = artifactLocationDecoder;
    return this;
  }

  public MockBlazeProjectDataBuilder setWorkspaceLanguageSettings(
      WorkspaceLanguageSettings workspaceLanguageSettings) {
    this.workspaceLanguageSettings = workspaceLanguageSettings;
    return this;
  }

  public MockBlazeProjectDataBuilder setSyncState(SyncState syncState) {
    this.syncState = syncState;
    return this;
  }

  public MockBlazeProjectDataBuilder setReverseDependencies(
      ImmutableMultimap<TargetKey, TargetKey> reverseDependencies) {
    this.reverseDependencies = reverseDependencies;
    return this;
  }

  public BlazeProjectData build() {
    TargetMap targetMap =
        this.targetMap != null ? this.targetMap : new TargetMap(ImmutableMap.of());
    ImmutableMap<String, String> blazeInfo =
        this.blazeInfo != null ? this.blazeInfo : ImmutableMap.of();
    BlazeRoots blazeRoots =
        this.blazeRoots != null
            ? this.blazeRoots
            : new BlazeRoots(
                new File(workspaceRoot.directory().getParentFile(), "exec_root"),
                new ExecutionRootPath("bin"),
                new ExecutionRootPath("gen"),
                null);
    BlazeVersionData blazeVersionData =
        this.blazeVersionData != null ? this.blazeVersionData : new BlazeVersionData();
    WorkspacePathResolver workspacePathResolver =
        this.workspacePathResolver != null
            ? this.workspacePathResolver
            : new WorkspacePathResolverImpl(workspaceRoot);
    ArtifactLocationDecoder artifactLocationDecoder =
        this.artifactLocationDecoder != null
            ? this.artifactLocationDecoder
            : new ArtifactLocationDecoderImpl(blazeRoots, workspacePathResolver);
    WorkspaceLanguageSettings workspaceLanguageSettings =
        this.workspaceLanguageSettings != null
            ? this.workspaceLanguageSettings
            : new WorkspaceLanguageSettings(WorkspaceType.JAVA, ImmutableSet.of());
    SyncState syncState =
        this.syncState != null ? this.syncState : new SyncState(ImmutableMap.of());
    ImmutableMultimap<TargetKey, TargetKey> reverseDependencies =
        this.reverseDependencies != null ? this.reverseDependencies : ImmutableMultimap.of();

    return new BlazeProjectData(
        syncTime,
        targetMap,
        blazeInfo,
        blazeRoots,
        blazeVersionData,
        workspacePathResolver,
        artifactLocationDecoder,
        workspaceLanguageSettings,
        syncState,
        reverseDependencies,
        null);
  }
}
