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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.async.FutureUtil;
import com.google.idea.blaze.base.async.executor.BlazeExecutor;
import com.google.idea.blaze.base.filecache.FileDiffer;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.JavaRuleIdeInfo;
import com.google.idea.blaze.base.ideinfo.RuleIdeInfo;
import com.google.idea.blaze.base.ideinfo.RuleKey;
import com.google.idea.blaze.base.model.SyncState;
import com.google.idea.blaze.base.prefetch.PrefetchService;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.Scope;
import com.google.idea.blaze.base.scope.output.PrintOutput;
import com.google.idea.blaze.base.scope.scopes.TimingScope;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.repackaged.devtools.build.lib.view.proto.Deps;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nullable;

/** Reads jdeps from the ide info result. */
public class JdepsFileReader {
  private static final Logger LOG = Logger.getInstance(JdepsFileReader.class);

  static class JdepsState implements Serializable {
    private static final long serialVersionUID = 4L;
    private ImmutableMap<File, Long> fileState = null;
    private Map<File, RuleKey> fileToRuleMap = Maps.newHashMap();
    private Map<RuleKey, List<String>> ruleToJdeps = Maps.newHashMap();
  }

  private static class Result {
    File file;
    RuleKey ruleKey;
    List<String> dependencies;

    public Result(File file, RuleKey ruleKey, List<String> dependencies) {
      this.file = file;
      this.ruleKey = ruleKey;
      this.dependencies = dependencies;
    }
  }

  /** Loads any updated jdeps files since the last invocation of this method. */
  @Nullable
  public JdepsMap loadJdepsFiles(
      Project project,
      BlazeContext parentContext,
      ArtifactLocationDecoder artifactLocationDecoder,
      Iterable<RuleIdeInfo> rulesToLoad,
      SyncState.Builder syncStateBuilder,
      @Nullable SyncState previousSyncState) {
    JdepsState oldState =
        previousSyncState != null ? previousSyncState.get(JdepsState.class) : null;
    JdepsState jdepsState =
        Scope.push(
            parentContext,
            (context) -> {
              context.push(new TimingScope("LoadJdepsFiles"));
              return doLoadJdepsFiles(
                  project, context, artifactLocationDecoder, oldState, rulesToLoad);
            });
    if (jdepsState == null) {
      return null;
    }
    syncStateBuilder.put(JdepsState.class, jdepsState);
    return ruleKey -> jdepsState.ruleToJdeps.get(ruleKey);
  }

  private JdepsState doLoadJdepsFiles(
      Project project,
      BlazeContext context,
      ArtifactLocationDecoder artifactLocationDecoder,
      @Nullable JdepsState oldState,
      Iterable<RuleIdeInfo> rulesToLoad) {
    JdepsState state = new JdepsState();
    if (oldState != null) {
      state.ruleToJdeps = Maps.newHashMap(oldState.ruleToJdeps);
      state.fileToRuleMap = Maps.newHashMap(oldState.fileToRuleMap);
    }

    Map<File, RuleKey> fileToRuleMap = Maps.newHashMap();
    for (RuleIdeInfo ruleIdeInfo : rulesToLoad) {
      assert ruleIdeInfo != null;
      JavaRuleIdeInfo javaRuleIdeInfo = ruleIdeInfo.javaRuleIdeInfo;
      if (javaRuleIdeInfo != null) {
        ArtifactLocation jdepsFile = javaRuleIdeInfo.jdepsFile;
        if (jdepsFile != null) {
          fileToRuleMap.put(artifactLocationDecoder.decode(jdepsFile), ruleIdeInfo.key);
        }
      }
    }

    List<File> updatedFiles = Lists.newArrayList();
    List<File> removedFiles = Lists.newArrayList();
    state.fileState =
        FileDiffer.updateFiles(
            oldState != null ? oldState.fileState : null,
            fileToRuleMap.keySet(),
            updatedFiles,
            removedFiles);

    ListenableFuture<?> fetchFuture =
        PrefetchService.getInstance().prefetchFiles(project, updatedFiles);
    if (!FutureUtil.waitForFuture(context, fetchFuture)
        .timed("FetchJdeps")
        .withProgressMessage("Reading jdeps files...")
        .run()
        .success()) {
      return null;
    }

    for (File removedFile : removedFiles) {
      RuleKey ruleKey = state.fileToRuleMap.remove(removedFile);
      if (ruleKey != null) {
        state.ruleToJdeps.remove(ruleKey);
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
                    RuleKey ruleKey = fileToRuleMap.get(updatedFile);
                    return new Result(updatedFile, ruleKey, dependencyStringList);
                  }
                } catch (FileNotFoundException e) {
                  LOG.info("Could not open jdeps file: " + updatedFile);
                }
                return null;
              }));
    }
    try {
      for (Result result : Futures.allAsList(futures).get()) {
        if (result != null) {
          state.fileToRuleMap.put(result.file, result.ruleKey);
          state.ruleToJdeps.put(result.ruleKey, result.dependencies);
        }
      }
      context.output(
          PrintOutput.log(
              String.format(
                  "Loaded %d jdeps files, total size %dkB",
                  updatedFiles.size(), totalSizeLoaded.get() / 1024)));
    } catch (InterruptedException | ExecutionException e) {
      LOG.error(e);
      return null;
    }
    return state;
  }

  private static <T> ListenableFuture<T> submit(Callable<T> callable) {
    return BlazeExecutor.getInstance().submit(callable);
  }
}
