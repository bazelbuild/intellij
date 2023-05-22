/*
 * Copyright 2022 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.common;

/** Context interface, used to track operation specific state and output. */
public interface Context<C extends Context<C>> {

  /**
   * A scoped facet of a scoped operation.
   *
   * <p>Attaches to a context and starts and ends with it.
   */
  interface Scope<C extends Context<?>> {
    /** Called when the scope is added to the context. */
    void onScopeBegin(C context);

    /** Called when the context scope is ending. */
    void onScopeEnd(C context);
  }

  C push(Scope<? super C> scope);

  <T extends Scope<?>> T getScope(Class<T> scopeClass);

  /**
   * Sends an output message.
   *
   * @param output The output. The specific subclass will determine how the output is handled.
   * @param <T> The type of {@code output}.
   */
  <T extends Output> void output(T output);

  void setHasError();

  /**
   * Log & display a message to the user when a user-initiated action fails.
   *
   * @param description A user readable failure message, including the high level IDE operation that
   *     failed.
   * @param t The exception that caused the failure.
   */
  default void handleException(String description, Throwable t) {}

  /**
   * Log & display a message to the user when a user-initiated action encounters non-fatal problems.
   *
   * @param description A user readable failure message, including the high level IDE operation that
   *     failed.
   * @param t The exception that caused the failure.
   */
  default void handleExceptionAsWarning(String description, Throwable t) {}
}
