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

import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.contents.FileContent;
import com.intellij.diff.merge.MergeResult;
import com.intellij.diff.requests.BinaryMergeRequestImpl;
import com.intellij.openapi.project.Project;
import com.intellij.util.Consumer;
import java.util.List;
import javax.annotation.Nullable;

/** #api191: constructor changed in 2019.2 */
public class BinaryMergeRequestImplAdapter extends BinaryMergeRequestImpl {

  public BinaryMergeRequestImplAdapter(
      @Nullable Project project,
      FileContent file,
      byte[] originalContent,
      List<DiffContent> contents,
      List<byte[]> byteContents,
      @Nullable String title,
      List<String> contentTitles,
      @Nullable Consumer<? super MergeResult> applyCallback) {
    super(
        project,
        file,
        originalContent,
        contents,
        byteContents,
        title,
        contentTitles,
        applyCallback);
  }
}
