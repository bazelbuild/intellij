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
package com.google.idea.blaze.base.qsync.cache;

import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.idea.blaze.base.command.buildresult.OutputArtifact;
import com.google.idea.blaze.common.Context;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.util.concurrency.AppExecutorUtil;
import java.nio.file.Path;
import java.util.List;

/** Copy a bunch of artifacts. */
public interface ArtifactFetcher<ArtifactT extends OutputArtifact> {
  ExtensionPointName<ArtifactFetcher<?>> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.qsync.ArtifactFetcher");

  ListeningExecutorService EXECUTOR =
      MoreExecutors.listeningDecorator(
          AppExecutorUtil.createBoundedApplicationPoolExecutor("ArtifactBulkCopyExecutor", 128));

  /** Copies a bunch of artifact to destination. */
  ListenableFuture<List<Path>> copy(
      ImmutableMap<? extends ArtifactT, Path> artifactToDest, Context<?> context);

  Class<ArtifactT> supportedArtifactType();
}
