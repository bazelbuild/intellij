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

import com.google.idea.blaze.qsync.project.BlazeProjectSnapshot;
import com.google.idea.blaze.qsync.query.QuerySpec;
import com.google.idea.blaze.qsync.query.QuerySummary;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * A project update that does nothing other than return the existing project state.
 *
 * <p>This is used when performing a partial sync when there have been no changes to the workspace
 * since the last sync.
 */
public class NoopProjectRefresh implements RefreshOperation {

  private final Supplier<BlazeProjectSnapshot> latestProjectSnapshotSupplier;

  public NoopProjectRefresh(Supplier<BlazeProjectSnapshot> latestProjectSnapshotSupplier) {
    this.latestProjectSnapshotSupplier = latestProjectSnapshotSupplier;
  }

  @Override
  public Optional<QuerySpec> getQuerySpec() {
    return Optional.empty();
  }

  @Override
  public void setQueryOutput(QuerySummary output) {}

  @Override
  public BlazeProjectSnapshot createBlazeProject() {
    return latestProjectSnapshotSupplier.get();
  }
}
