/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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
package com.google.idea.sdkcompat.vcs;

import com.intellij.diff.DiffRequestFactoryImpl;
import com.intellij.diff.InvalidDiffRequestException;
import com.intellij.diff.merge.MergeRequest;
import com.intellij.diff.merge.MergeResult;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.vfs.AbstractVcsVirtualFile;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Adapter for {@link com.google.devtools.intellij.piper.resolve.CustomDiffRequestFactory}.
 *
 * <p>#api183 -- wildcard bounds added to method signatures in 2019.1.
 */
public abstract class PiperCustomDiffRequestFactoryAdapter extends DiffRequestFactoryImpl {

  @Override
  public MergeRequest createMergeRequest(
      @Nullable Project project,
      VirtualFile output,
      List<byte[]> byteContents,
      @Nullable String title,
      List<String> contentTitles,
      @Nullable Consumer<? super MergeResult> applyCallback)
      throws InvalidDiffRequestException {
    if (!(output instanceof AbstractVcsVirtualFile)) {
      return super.createMergeRequest(
          project, output, byteContents, title, contentTitles, applyCallback);
    }
    return noopMergeRequest(project, output, byteContents, title, contentTitles, applyCallback);
  }

  @Override
  public MergeRequest createBinaryMergeRequest(
      @Nullable Project project,
      VirtualFile output,
      List<byte[]> byteContents,
      @Nullable String title,
      List<String> contentTitles,
      @Nullable Consumer<? super MergeResult> applyCallback)
      throws InvalidDiffRequestException {
    if (!(output instanceof AbstractVcsVirtualFile)) {
      return super.createBinaryMergeRequest(
          project, output, byteContents, title, contentTitles, applyCallback);
    }
    return noopMergeRequest(project, output, byteContents, title, contentTitles, applyCallback);
  }

  protected abstract MergeRequest noopMergeRequest(
      @Nullable Project project,
      VirtualFile output,
      List<byte[]> byteContents,
      @Nullable String title,
      List<String> contentTitles,
      @Nullable Consumer<? super MergeResult> applyCallback)
      throws InvalidDiffRequestException;
}
