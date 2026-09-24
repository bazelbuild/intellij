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
package com.google.idea.blaze.base.buildmodifier;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.CharStreams;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.intellij.formatting.FormattingContext;
import com.intellij.formatting.service.AsyncDocumentFormattingService;
import com.intellij.formatting.service.AsyncFormattingRequest;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Optional;
import javax.annotation.Nullable;

/** Formatting support for BUILD/bzl sources, delegating to an external 'buildifier' binary. */
public final class BuildifierFormattingService extends AsyncDocumentFormattingService {

  @Override
  @Nullable
  protected FormattingTask createFormattingTask(AsyncFormattingRequest request) {
    final var ctx = request.getContext();

    BuildFile buildFile = (BuildFile) ctx.getContainingFile();
    Optional<String> binary = getBinary(ctx.getProject());
    if (binary.isEmpty()) {
      notifyBinaryMissing(ctx.getProject());
      return null;
    }

    return new BuildifierFormattingTask(
        request,
        BuildFileFormatter.getCommandLineArgs(binary.get(), buildFile),
        BuildFileFormatter.workingDirectory(buildFile)
    );
  }

  @Override
  protected String getNotificationGroupId() {
    return "CodeFormatter";
  }

  @Override
  protected String getName() {
    return "buildifier";
  }

  @Override
  protected void prepareForFormatting(Document document, FormattingContext context) {
    // The default implementation saves the document so that the formatter can read it from disk.
    // buildifier is fed the document text over stdin instead, so there is nothing to prepare.
  }

  @Override
  public ImmutableSet<Feature> getFeatures() {
    // Although buildifier does NOT support range formatting, we assume it does and then just format
    // the whole file
    return ImmutableSet.of(Feature.FORMAT_FRAGMENTS);
  }

  @Override
  public boolean canFormat(PsiFile file) {
    return file instanceof BuildFile;
  }

  private static Optional<String> getBinary(Project project) {
    for (BuildifierBinaryProvider provider : BuildifierBinaryProvider.EP_NAME.getExtensions()) {
      String path = provider.getBuildifierBinaryPath(project);
      if (!Strings.isNullOrEmpty(path)) {
        return Optional.of(path);
      }
    }
    return Optional.empty();
  }

  private static void notifyBinaryMissing(Project project) {
    if (BuildifierDownloader.canDownload()) {
      BuildifierNotification.showDownloadNotification(project);
    } else {
      BuildifierNotification.showNotFoundNotification();
    }
  }

  private static final class BuildifierFormattingTask implements FormattingTask {
    private final AsyncFormattingRequest request;
    private final ImmutableList<String> args;
    @Nullable private final File workingDirectory;
    private Process process;

    public BuildifierFormattingTask(
        AsyncFormattingRequest request,
        ImmutableList<String> args,
        @Nullable File workingDirectory) {
      this.request = request;
      this.args = args;
      this.workingDirectory = workingDirectory;
    }

    @Override
    public void run() {
      try {
        process = new ProcessBuilder(args).directory(workingDirectory).start();
        process.getOutputStream().write(request.getDocumentText().getBytes(UTF_8));
        process.getOutputStream().close();
        BufferedReader reader =
            new BufferedReader(new InputStreamReader(process.getInputStream(), UTF_8));
        String formattedText = CharStreams.toString(reader);
        boolean exited = process.waitFor(10, SECONDS);

        if (!exited) {
          process.destroyForcibly();
          request.onError("Error running buildifier", "process timed out.");
        } else if (process.exitValue() == 0) {
          request.onTextReady(formattedText);
        } else {
          request.onError(
              "Please fix syntax errors",
              "buildifier failed. Does "
                  + request.getContext().getContainingFile().getName()
                  + " have syntax errors?");
        }
      } catch (InterruptedException e) {
        process.destroy();
        Thread.currentThread().interrupt();
      } catch (IOException e) {
        request.onError("Error running buildifier", e.getMessage());
      }
    }

    @Override
    public boolean cancel() {
      if (process != null) {
        process.destroy();
      }
      return true;
    }

    @Override
    public boolean isRunUnderProgress() {
      return true;
    }
  }
}
