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
package com.google.idea.blaze.base.logging.utils.querysync;

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.logging.EventLoggingService;
import com.google.idea.blaze.base.logging.utils.querysync.QuerySyncActionStats.Result;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.BlazeScope;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.vfs.VirtualFile;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.eclipse.sisu.Nullable;

/** Stores @{QuerySyncActionStats} so that it can be logged by the BlazeContext creator owner. */
public class QuerySyncActionStatsScope implements BlazeScope {
  private final QuerySyncActionStats.Builder builder;
  private final TimeSource timeSource;

  public QuerySyncActionStatsScope(Class<?> actionClass, @Nullable AnActionEvent event) {
    this(actionClass, event, ImmutableSet.of());
  }

  public QuerySyncActionStatsScope(
      Class<?> actionClass, @Nullable AnActionEvent event, VirtualFile virtualFile) {
    this(actionClass, event, ImmutableSet.of(virtualFile));
  }

  public QuerySyncActionStatsScope(
      Class<?> actionClass,
      @Nullable AnActionEvent event,
      ImmutableCollection<VirtualFile> requestFiles) {
    this(actionClass, event, requestFiles, () -> Instant.now());
  }

  @VisibleForTesting
  public QuerySyncActionStatsScope(
      Class<?> actionClass,
      @Nullable AnActionEvent event,
      ImmutableCollection<VirtualFile> requestFiles,
      TimeSource timeSource) {
    builder =
        QuerySyncActionStats.builder()
            .handleActionClass(actionClass)
            .handleActionEvent(event)
            .setRequestedFiles(
                requestFiles.stream().map(VirtualFile::toNioPath).collect(toImmutableSet()));
    this.timeSource = timeSource;
  }

  public QuerySyncActionStats.Builder getBuilder() {
    return builder;
  }

  public static Optional<QuerySyncActionStats.Builder> fromContext(BlazeContext context) {
    return Optional.ofNullable(context.getScope(QuerySyncActionStatsScope.class))
        .map(QuerySyncActionStatsScope::getBuilder);
  }

  @Override
  public void onScopeBegin(BlazeContext context) {
    builder.setStartTime(timeSource.now());
  }

  private Result getSyncResult(BlazeContext context) {
    if (context.isCancelled()) {
      return Result.CANCELLED;
    }
    if (context.hasErrors()) {
      return Result.FAILURE;
    }
    if (context.hasWarnings()) {
      return Result.SUCCESS_WITH_WARNING;
    }
    return Result.SUCCESS;
  }

  /** Called when the context scope is ending. */
  @Override
  public void onScopeEnd(BlazeContext context) {
    fromContext(context)
        .ifPresent(
            builder ->
                EventLoggingService.getInstance()
                    .log(
                        builder
                            .setTotalClockTime(Duration.between(builder.startTime(), Instant.now()))
                            .setResult(getSyncResult(context))
                            .build()));
  }

  /**
   * Provider for the current value of "now" for users. This allows unit tests to set now
   * specifically.
   */
  public interface TimeSource {
    Instant now();
  }
}
