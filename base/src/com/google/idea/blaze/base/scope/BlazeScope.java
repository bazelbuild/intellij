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
package com.google.idea.blaze.base.scope;

import org.jetbrains.annotations.NotNull;

/**
 * A scoped facet of a scoped operation.
 *
 * <p>
 *
 * <p>Attaches to a blaze context and starts and ends with it.
 */
public interface BlazeScope {
  /** Called when the scope is added to the context. */
  void onScopeBegin(@NotNull BlazeContext context);

  /** Called when the context scope is ending. */
  void onScopeEnd(@NotNull BlazeContext context);
}
