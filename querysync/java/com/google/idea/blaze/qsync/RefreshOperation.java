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

import com.google.idea.blaze.exception.BuildException;
import com.google.idea.blaze.qsync.project.BlazeProjectSnapshot;
import com.google.idea.blaze.qsync.query.QuerySpec;
import com.google.idea.blaze.qsync.query.QuerySummary;
import java.io.IOException;
import java.util.Optional;

/**
 * Represents an operation that is refreshing a {@link BlazeProjectSnapshot}.
 *
 * <p>To use this interface:
 *
 * <ol>
 *   <li>Acquire an implementation, e.g. from {@link ProjectRefresher}
 *   <li>Run a {@code query} invocation based on the spec from {@link #getQuerySpec()}
 *   <li>Pass the results of that query to {@link #setQueryOutput(QuerySummary)}
 *   <li>Call {@link #createBlazeProject()} to get the updated project snapshot.
 * </ol>
 */
public interface RefreshOperation {

  /** Returns the spec of the query to be run for this strategy. */
  Optional<QuerySpec> getQuerySpec() throws IOException;

  /** Passes the output from the query specified by {@link #getQuerySpec()}. */
  void setQueryOutput(QuerySummary output);

  /**
   * Creates the new project snapshot. Must only be called after {@link
   * #setQueryOutput(QuerySummary)}.
   */
  BlazeProjectSnapshot createBlazeProject() throws BuildException;
}
