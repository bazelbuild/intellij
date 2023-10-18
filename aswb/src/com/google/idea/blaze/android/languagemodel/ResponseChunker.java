/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.languagemodel;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.android.studio.ml.SmlConversation;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.ext.GenerateAnswersResponse;
import com.google.idea.blaze.ext.RelatedResources;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Processes response. */
class ResponseChunker {
  private static final String CODE_FENCE_MARKER = "```";

  // For now, we just use an empty trace id. Eventually, we may want to use some unique id per
  // response.
  private static final int TRACE_ID = 0;

  ImmutableList<SmlConversation.ResponseChunk> chunk(GenerateAnswersResponse response) {
    List<SmlConversation.ResponseChunk> chunks = new ArrayList<>();

    chunks.addAll(chunkText(response.getResponse().getText()));
    SmlConversation.ResponseChunk references =
        references(response.getResponse().getRelatedResourcesList());
    if (references != null) {
      chunks.add(references);
    }

    return ImmutableList.copyOf(chunks);
  }

  @Nullable
  private SmlConversation.ResponseChunk references(List<RelatedResources> resources) {
    if (resources.isEmpty()) {
      return null;
    }

    ImmutableList<String> urls =
        resources.stream()
            .map(RelatedResources::getUrl)
            .map(s -> "http://" + s)
            .collect(toImmutableList());
    return new SmlConversation.CitationsChunk(TRACE_ID, urls);
  }

  @NotNull
  private static ImmutableList<SmlConversation.ResponseChunk> chunkText(String text) {
    // If there are no code chunks, then the entire response is a single text chunk.
    if (!text.contains("```")) {
      return ImmutableList.of(new SmlConversation.TextChunk(text, 0, false));
    }

    // Split the response into separate text and code chunks.
    List<SmlConversation.ResponseChunk> chunks = new ArrayList<>();
    var lines = Splitter.on('\n').split(text);
    var sb = new StringBuilder();
    var inCodeBlock = false;

    for (String line : lines) {
      if (line.startsWith(CODE_FENCE_MARKER) && inCodeBlock) {
        chunks.add(new SmlConversation.CodeChunk(sb.toString(), TRACE_ID));
        inCodeBlock = false;
        sb = new StringBuilder();
      } else if (line.startsWith(CODE_FENCE_MARKER)) {
        if (sb.length() > 0) {
          chunks.add(new SmlConversation.TextChunk(sb.toString(), TRACE_ID, false));
        }
        inCodeBlock = true;
        sb = new StringBuilder();
      } else {
        sb.append(line);
        sb.append("\n");
      }
    }

    if (sb.length() > 0) {
      if (inCodeBlock) {
        chunks.add(new SmlConversation.CodeChunk(sb.toString(), TRACE_ID));
      } else {
        chunks.add(new SmlConversation.TextChunk(sb.toString(), TRACE_ID, false));
      }
    }

    return ImmutableList.copyOf(chunks);
  }
}
