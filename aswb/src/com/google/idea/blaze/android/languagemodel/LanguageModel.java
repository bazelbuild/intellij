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

import com.android.studio.ml.AbstractConversationModel;
import com.android.studio.ml.ConversationError;
import com.android.studio.ml.SmlConversation;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.ext.GenerateAnswersRequest;
import com.google.idea.blaze.ext.GenerateAnswersResponse;
import java.io.IOException;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/** Partial implementation of an AbstractConversationModel. */
public abstract class LanguageModel extends AbstractConversationModel {

  protected abstract GenerateAnswersResponse generateAnswers(GenerateAnswersRequest request)
      throws IOException;

  @NotNull
  @Override
  public List<SmlConversation.ResponseChunk> nonStreamingResponse(
      @NotNull SmlConversation.Request request) {
    GenerateAnswersResponse response;

    try {
      response =
          generateAnswers(GenerateAnswersRequest.newBuilder().setText(request.getText()).build());
    } catch (IOException e) {
      return errorChunk("Error talking to language model: " + e.getMessage());
    }

    return new ResponseChunker().chunk(response);
  }

  private ImmutableList<SmlConversation.ResponseChunk> errorChunk(String msg) {
    return ImmutableList.of(new SmlConversation.ErrorChunk(0, new ConversationError.Generic(msg)));
  }
}
