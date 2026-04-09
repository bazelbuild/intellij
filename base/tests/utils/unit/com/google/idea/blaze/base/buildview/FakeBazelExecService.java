/*
 * Copyright 2026 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.buildview;

import com.google.errorprone.annotations.MustBeClosed;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.command.buildresult.BuildResult;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.sync.aspects.BlazeBuildOutputs;
import com.intellij.execution.ExecutionException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import javax.annotation.Nullable;

/**
 * A fake implementation of {@link BazelExecService} for use in tests.
 *
 * <p>Configure responses via {@link #setBuildHandler} and {@link #setExecHandler}, and inspect
 * issued commands via {@link #getIssuedCommands}.
 */
public class FakeBazelExecService implements BazelExecService {

  @FunctionalInterface
  public interface BuildHandler {
    BlazeBuildOutputs handle(BlazeContext ctx, BlazeCommand.Builder cmdBuilder) throws ExecutionException;
  }

  @FunctionalInterface
  public interface ExecHandler {
    ExecResult handle(BlazeContext ctx, BlazeCommand.Builder cmdBuilder) throws ExecutionException;
  }

  private final List<BlazeCommand.Builder> issuedCommands = new ArrayList<>();

  @Nullable private BuildHandler buildHandler;
  @Nullable private ExecHandler execHandler;

  public FakeBazelExecService() {
    this.buildHandler = (ctx, cmd) -> BlazeBuildOutputs.noOutputs(BuildResult.SUCCESS);
    this.execHandler = (ctx, cmd) -> createExecResult("", 0);
  }

  public void setBuildHandler(BuildHandler handler) {
    this.buildHandler = handler;
  }

  public void setExecHandler(ExecHandler handler) {
    this.execHandler = handler;
  }

  /** Returns all commands that were issued to this service, in order. */
  public List<BlazeCommand.Builder> getIssuedCommands() {
    return issuedCommands;
  }

  @Override
  public BlazeBuildOutputs build(BlazeContext ctx, BlazeCommand.Builder cmdBuilder) throws ExecutionException {
    issuedCommands.add(cmdBuilder);
    return buildHandler.handle(ctx, cmdBuilder);
  }

  @MustBeClosed
  @Override
  public ExecResult exec(BlazeContext ctx, BlazeCommand.Builder cmdBuilder) throws ExecutionException {
    issuedCommands.add(cmdBuilder);
    return execHandler.handle(ctx, cmdBuilder);
  }

  /** Convenience method to create an {@link ExecResult} with the given stdout and exit code. */
  public static ExecResult createExecResult(String stdout, int exitCode) {
    try {
      Path tempFile = Files.createTempFile("fake-bazel-exec-", ".stdout");
      Files.writeString(tempFile, stdout, StandardCharsets.UTF_8);
      return new ExecResult(exitCode, tempFile);
    } catch (IOException e) {
      throw new RuntimeException("Failed to create fake ExecResult", e);
    }
  }
}
