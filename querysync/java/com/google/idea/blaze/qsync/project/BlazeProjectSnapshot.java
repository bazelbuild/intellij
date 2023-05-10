/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.qsync.project;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.common.BuildTarget;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.qsync.query.PackageSet;
import java.nio.file.Path;
import javax.annotation.Nullable;

/**
 * A fully sync'd project at a point in time. This consists of:
 *
 * <ul>
 *   <li>The IntelliJ project structure, presented as a proto.
 *   <li>The {@link PostQuerySyncData} that is was derived from.
 * </ul>
 *
 * <p>This class is immutable, any modifications to the project will yield a new instance.
 */
@AutoValue
public abstract class BlazeProjectSnapshot {

  public abstract PostQuerySyncData queryData();

  public abstract BuildGraphData graph();

  /** Project proto reflecting the structure of the IJ project. */
  public abstract ProjectProto.Project project();

  public static Builder builder() {
    return new AutoValue_BlazeProjectSnapshot.Builder();
  }

  /**
   * Returns the set of build packages in the query output.
   *
   * <p>The packages are workspace relative paths that contain a BUILD file.
   */
  public PackageSet getPackages() {
    return queryData().querySummary().getPackages();
  }

  /**
   * Given a path to a file it returns the target that owns the file. Note that in general there
   * could be multiple targets that compile a file, but we try to choose the smallest one, as it
   * would have everything the file needs to be compiled.
   *
   * @param path a workspace relative path.
   */
  @Nullable
  public Label getTargetOwner(Path path) {
    return graph().getTargetOwner(path);
  }

  /**
   * For a given path to a file, returns all the targets outside the project that this file needs to
   * be edited fully.
   */
  @Nullable
  public ImmutableSet<Label> getFileDependencies(Path path) {
    return graph().getFileDependencies(path);
  }

  /** Returns mapping of targets to {@link BuildTarget} */
  public ImmutableMap<Label, BuildTarget> getTargetMap() {
    return graph().targetMap();
  }

  /** Builder for {@link BlazeProjectSnapshot}. */
  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder queryData(PostQuerySyncData value);

    public abstract Builder graph(BuildGraphData value);

    public abstract Builder project(ProjectProto.Project value);

    public abstract BlazeProjectSnapshot build();
  }
}
