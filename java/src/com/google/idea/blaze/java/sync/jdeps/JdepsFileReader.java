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
package com.google.idea.blaze.java.sync.jdeps;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.build.lib.view.proto.Deps;
import com.google.idea.blaze.base.async.FutureUtil;
import com.google.idea.blaze.base.async.executor.BlazeExecutor;
import com.google.idea.blaze.base.filecache.FileDiffer;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.JavaIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.model.SyncState;
import com.google.idea.blaze.base.prefetch.PrefetchService;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.Scope;
import com.google.idea.blaze.base.scope.output.PrintOutput;
import com.google.idea.blaze.base.scope.scopes.TimingScope;
import com.google.idea.blaze.base.scope.scopes.TimingScope.EventType;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nullable;

/** Reads jdeps from the ide info result. */
public class JdepsFileReader {
  private static final Logger logger = Logger.getInstance(JdepsFileReader.class);

  private static class Result {
    File file;
    TargetKey targetKey;
    List<String> dependencies;

    public Result(File file, TargetKey targetKey, List<String> dependencies) {
      this.file = file;
      this.targetKey = targetKey;
      this.dependencies = dependencies;
    }
  }

  /** Loads any updated jdeps files since the last invocation of this method. */
  @Nullable
  public JdepsMap loadJdepsFiles(
      BlazeContext parentContext,
      ArtifactLocationDecoder artifactLocationDecoder,
      Iterable<TargetIdeInfo> targetsToLoad,
      SyncState.Builder syncStateBuilder,
      @Nullable SyncState previousSyncState) {
    JdepsState oldState =
        previousSyncState != null ? previousSyncState.get(JdepsState.class) : null;
    JdepsState jdepsState =
        Scope.push(
            parentContext,
            (context) -> {
              context.push(new TimingScope("LoadJdepsFiles", EventType.Other));
              try {
                return doLoadJdepsFiles(context, artifactLocationDecoder, oldState, targetsToLoad);
              } catch (InterruptedException e) {
                throw new ProcessCanceledException(e);
              } catch (ExecutionException e) {
                context.setHasError();
                logger.error(e);
              }
              return null;
            });
    if (jdepsState == null) {
      return null;
    }
    syncStateBuilder.put(jdepsState);
    return jdepsState.targetToJdeps::get;
  }

  @Nullable
  private JdepsState doLoadJdepsFiles(
      BlazeContext context,
      ArtifactLocationDecoder artifactLocationDecoder,
      @Nullable JdepsState oldState,
      Iterable<TargetIdeInfo> targetsToLoad)
      throws InterruptedException, ExecutionException {
    JdepsState.Builder state = JdepsState.builder();
    if (oldState != null) {
      state.targetToJdeps = Maps.newHashMap(oldState.targetToJdeps);
      state.fileToTargetMap = Maps.newHashMap(oldState.fileToTargetMap);
    }

    Map<File, TargetKey> fileToTargetMap = Maps.newHashMap();
    for (TargetIdeInfo target : targetsToLoad) {
      assert target != null;
      JavaIdeInfo javaIdeInfo = target.getJavaIdeInfo();
      if (javaIdeInfo != null) {
        ArtifactLocation jdepsFile = javaIdeInfo.getJdepsFile();
        if (jdepsFile != null) {
          fileToTargetMap.put(artifactLocationDecoder.decode(jdepsFile), target.getKey());
        }
      }
    }

    List<File> updatedFiles = Lists.newArrayList();
    List<File> removedFiles = Lists.newArrayList();
    state.fileState =
        FileDiffer.updateFiles(
            oldState != null ? oldState.fileState : null,
            fileToTargetMap.keySet(),
            updatedFiles,
            removedFiles);

    ListenableFuture<?> fetchFuture =
        PrefetchService.getInstance().prefetchFiles(updatedFiles, true, false);
    if (!FutureUtil.waitForFuture(context, fetchFuture)
        .timed("FetchJdeps", EventType.Prefetching)
        .withProgressMessage("Reading jdeps files...")
        .run()
        .success()) {
      return null;
    }

    for (File removedFile : removedFiles) {
      TargetKey targetKey = state.fileToTargetMap.remove(removedFile);
      if (targetKey != null) {
        state.targetToJdeps.remove(targetKey);
      }
    }

    AtomicLong totalSizeLoaded = new AtomicLong(0);

    List<ListenableFuture<Result>> futures = Lists.newArrayList();
    for (File updatedFile : updatedFiles) {
      futures.add(
          submit(
              () -> {
                totalSizeLoaded.addAndGet(updatedFile.length());
                try (InputStream inputStream = new FileInputStream(updatedFile)) {
                  Deps.Dependencies dependencies = Deps.Dependencies.parseFrom(inputStream);
                  if (dependencies != null) {
                    List<String> dependencyStringList = Lists.newArrayList();
                    for (Deps.Dependency dependency : dependencies.getDependencyList()) {
                      // We only want explicit or implicit deps that were
                      // actually resolved by the compiler, not ones that are
                      // available for use in the same package
                      if (dependency.getKind() == Deps.Dependency.Kind.EXPLICIT
                          || dependency.getKind() == Deps.Dependency.Kind.IMPLICIT) {
                        dependencyStringList.add(dependency.getPath());
                      }
                    }
                    TargetKey targetKey = fileToTargetMap.get(updatedFile);
                    return new Result(updatedFile, targetKey, dependencyStringList);
                  }
                } catch (FileNotFoundException e) {
                  logger.info("Could not open jdeps file: " + updatedFile);
                }
                return null;
              }));
    }
      for (Result result : Futures.allAsList(futures).get()) {
        if (result != null) {
          state.fileToTargetMap.put(result.file, result.targetKey);
          state.targetToJdeps.put(result.targetKey, result.dependencies);
        }
      }
      context.output(
          PrintOutput.log(
              String.format(
                  "Loaded %d jdeps files, total size %dkB",
                  updatedFiles.size(), totalSizeLoaded.get() / 1024)));
    return state.build();
  }

  private static <T> ListenableFuture<T> submit(Callable<T> callable) {
    return BlazeExecutor.getInstance().submit(callable);
  }
}
