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
package com.google.idea.blaze.scala.sync.model;

import com.google.devtools.intellij.model.ProjectData;
import com.google.idea.blaze.base.model.SyncData;
import com.google.idea.blaze.java.sync.model.BlazeJavaImportResult;
import javax.annotation.Nullable;

/** Sync data for the scala plugin. */
public class BlazeScalaSyncData implements SyncData<ProjectData.BlazeJavaSyncData> {
  private final BlazeScalaImportResult importResult;

  public BlazeScalaSyncData(BlazeScalaImportResult importResult) {
    this.importResult = importResult;
  }

  /**
   * Reusing {@link ProjectData.BlazeJavaSyncData} since {@link BlazeScalaImportResult} is a subset
   * of {@link BlazeJavaImportResult}.
   */
  private static BlazeScalaSyncData fromProto(ProjectData.BlazeJavaSyncData proto) {
    return new BlazeScalaSyncData(BlazeScalaImportResult.fromProto(proto.getImportResult()));
  }

  @Override
  public ProjectData.BlazeJavaSyncData toProto() {
    return ProjectData.BlazeJavaSyncData.newBuilder()
        .setImportResult(importResult.toProto())
        .build();
  }

  public BlazeScalaImportResult getImportResult() {
    return importResult;
  }

  @Override
  public void insert(ProjectData.SyncState.Builder builder) {
    builder.setBlazeScalaSyncData(toProto());
  }

  static class Extractor implements SyncData.Extractor<BlazeScalaSyncData> {
    @Nullable
    @Override
    public BlazeScalaSyncData extract(ProjectData.SyncState syncState) {
      return syncState.hasBlazeScalaSyncData()
          ? BlazeScalaSyncData.fromProto(syncState.getBlazeScalaSyncData())
          : null;
    }
  }
}
