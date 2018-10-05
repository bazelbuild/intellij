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
package com.google.idea.sdkcompat.query;

import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;

/** Compat for {@link QueryExecutor} #api181 */
public abstract class QueryExecutorAdapter<R, P> implements QueryExecutor<R, P> {
  @Override
  public final boolean execute(P params, Processor<? super R> processor) {
    return executeImpl(params, processor);
  }

  protected abstract boolean executeImpl(P params, Processor<? super R> processor);
}
