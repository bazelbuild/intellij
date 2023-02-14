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
package com.google.idea.blaze.qsync;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.idea.blaze.qsync.project.ProjectProto;
import com.google.idea.blaze.qsync.query.Query;
import com.google.idea.blaze.qsync.query.QuerySummary;
import com.google.idea.blaze.qsync.vcs.VcsState;
import java.nio.file.Path;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * Class that encapsulates a blaze project in intellij at a point in time.
 *
 * <p>This class is immutable, any modifications to the project will yield a new instance.
 */
@AutoValue
public abstract class BlazeProjectSnapshot {

  public static final BlazeProjectSnapshot EMPTY =
      builder()
          .graph(BuildGraphData.EMPTY)
          .project(ProjectProto.Project.getDefaultInstance())
          .projectExcludes(ImmutableList.of())
          .projectIncludes(ImmutableList.of())
          .vcsState(Optional.empty())
          .queryOutput(Query.Summary.getDefaultInstance())
          .build();

  /** The set of package roots included in the project. */
  public abstract ImmutableList<Path> projectIncludes();

  /** The set of package roots excluded from the project. */
  public abstract ImmutableList<Path> projectExcludes();

  /** Output of the {@code query} invocation that this project was derived from. */
  public abstract QuerySummary queryOutput();

  public abstract BuildGraphData graph();

  /** State of the projects VCS when this snapshot was created. */
  public abstract Optional<VcsState> vcsState();

  /** Project proto reflecting the structure of the IJ project. */
  public abstract ProjectProto.Project project();

  Builder toBuilder() {
    // Note we don't use the standard autovalue toBuilder() here as that includes *all* details
    // from the current instance, most of which we don't want, and may result in subtle bugs if
    // we fail to replace it.
    return builder().projectIncludes(projectIncludes()).projectExcludes(projectExcludes());
  }

  static Builder builder() {
    return new AutoValue_BlazeProjectSnapshot.Builder();
  }

  /**
   * Returns the set of build packages in the query output.
   *
   * <p>The packages are workspace relative paths that contain a BUILD file.
   */
  public ImmutableSet<Path> getPackages() {
    return queryOutput().getPackages();
  }

  /**
   * Given a path to a file it returns the target that owns the file. Note that in general there
   * could be multiple targets that compile a file, but we try to choose the smallest one, as it
   * would have everything the file needs to be compiled.
   *
   * @param path a workspace relative path.
   */
  public String getTargetOwner(Path path) {
    return graph().getTargetOwner(path);
  }

  /**
   * For a given path to a file, returns all the targets outside the project that this file needs to
   * be edited fully.
   */
  @Nullable
  public ImmutableSet<String> getFileDependencies(Path path) {
    return graph().getFileDependencies(path);
  }

  @AutoValue.Builder
  abstract static class Builder {

    public abstract Builder projectIncludes(ImmutableList<Path> value);

    public abstract Builder projectExcludes(ImmutableList<Path> value);

    public abstract Builder queryOutput(QuerySummary value);

    @CanIgnoreReturnValue
    public Builder queryOutput(Query.Summary value) {
      return queryOutput(QuerySummary.create(value));
    }

    public abstract Builder graph(BuildGraphData value);

    public abstract Builder vcsState(Optional<VcsState> value);

    public abstract Builder project(ProjectProto.Project value);

    public abstract BlazeProjectSnapshot build();
  }
}
