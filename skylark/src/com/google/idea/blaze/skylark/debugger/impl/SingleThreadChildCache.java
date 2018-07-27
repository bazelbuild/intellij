/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.skylark.debugger.impl;

import com.google.common.collect.ImmutableList;
import com.google.devtools.build.lib.skylarkdebugging.SkylarkDebuggingProtos;
import com.google.devtools.build.lib.skylarkdebugging.SkylarkDebuggingProtos.DebugEvent;
import com.google.devtools.build.lib.skylarkdebugging.SkylarkDebuggingProtos.DebugRequest;
import com.google.devtools.build.lib.skylarkdebugging.SkylarkDebuggingProtos.GetChildrenRequest;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.annotation.Nullable;

/**
 * A cache of 'getChildren' results for a currently-paused thread. This state is retained only while
 * the thread is paused.
 */
class SingleThreadChildCache {

  private final long threadId;
  private final ConcurrentMap<Long, List<SkylarkDebuggingProtos.Value>> identifierToChildrenMap =
      new ConcurrentHashMap<>();

  SingleThreadChildCache(long threadId) {
    this.threadId = threadId;
  }

  @Nullable
  List<SkylarkDebuggingProtos.Value> getChildren(
      DebugClientTransport transport, SkylarkDebuggingProtos.Value value) {
    // protocol specifies a non-zero ID for values with children
    if (!value.getHasChildren() || value.getId() == 0) {
      return ImmutableList.of();
    }
    return identifierToChildrenMap.computeIfAbsent(
        value.getId(), id -> queryChildren(transport, value));
  }

  @Nullable
  private List<SkylarkDebuggingProtos.Value> queryChildren(
      DebugClientTransport transport, SkylarkDebuggingProtos.Value value) {
    GetChildrenRequest request =
        GetChildrenRequest.newBuilder().setThreadId(threadId).setValueId(value.getId()).build();
    DebugEvent response = transport.sendRequest(DebugRequest.newBuilder().setGetChildren(request));
    return response == null ? null : response.getGetChildren().getChildrenList();
  }
}
