/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.common.artifact;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.util.concurrent.MoreExecutors.newDirectExecutorService;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.SettableFuture;
import com.google.idea.blaze.common.Context;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Writes fake artifacts on demands, fabricating their contents.
 *
 * <p>After a call to {@link #copy(ImmutableMap, Context)}, nothing it done until {@link
 * #executePendingTasks()} is called. After that is called, the fetched artifacts will be written to
 * disk and the future(s) completed.
 */
public class TestArtifactFetcher implements ArtifactFetcher<OutputArtifact> {

  private final List<Runnable> pendingTasks = Lists.newArrayList();
  private final Set<String> requestedDigests = Sets.newHashSet();
  private final Set<String> completedDigests = Sets.newHashSet();
  private final ListeningExecutorService executor = newDirectExecutorService();

  @Override
  public ListenableFuture<?> copy(
      ImmutableMap<? extends OutputArtifact, ArtifactDestination> artifactToDest,
      Context<?> context) {
    requestedDigests.addAll(
        artifactToDest.keySet().stream().map(OutputArtifact::getDigest).collect(toImmutableList()));
    SettableFuture<?> done = SettableFuture.create();
    pendingTasks.add(
        () ->
            done.setFuture(
                executor.submit(
                    () -> {
                      for (Entry<? extends OutputArtifact, ArtifactDestination> entry :
                          artifactToDest.entrySet()) {
                        Files.writeString(
                            entry.getValue().path,
                            getExpectedArtifactContents(entry.getKey().getDigest()),
                            StandardCharsets.UTF_8);
                        completedDigests.add(entry.getKey().getDigest());
                      }
                      return null;
                    })));
    return done;
  }

  public String getExpectedArtifactContents(String digest) {
    return String.format("Artifact created by %s\n%s", TestArtifactFetcher.class.getName(), digest);
  }

  public void executePendingTasks() {
    pendingTasks.forEach(Runnable::run);
    pendingTasks.clear();
  }

  public void executeNewestTask() {
    pendingTasks.remove(pendingTasks.size() - 1).run();
  }

  public void executeOldestTask() {
    pendingTasks.remove(0).run();
  }

  public void flushTasks() {
    while (!pendingTasks.isEmpty()) {
      executeOldestTask();
    }
  }

  public ImmutableSet<String> takeRequestedDigests() {
    ImmutableSet<String> requested = ImmutableSet.copyOf(requestedDigests);
    requestedDigests.clear();
    return requested;
  }

  public ImmutableSet<String> getCompletedDigests() {
    return ImmutableSet.copyOf(completedDigests);
  }

  @Override
  public Class<OutputArtifact> supportedArtifactType() {
    return OutputArtifact.class;
  }
}
