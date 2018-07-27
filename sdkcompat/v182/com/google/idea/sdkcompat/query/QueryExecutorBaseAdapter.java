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

import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.util.Processor;

/** Compat for {@link QueryExecutorBase} #api181 */
public abstract class QueryExecutorBaseAdapter<R, P> extends QueryExecutorBase<R, P> {
  protected QueryExecutorBaseAdapter(boolean requireReadAction) {
    super(requireReadAction);
  }

  protected QueryExecutorBaseAdapter() {
    super(false);
  }

  @Override
  public final void processQuery(P params, Processor<? super R> processor) {
    processQueryImpl(params, processor);
  }

  protected abstract void processQueryImpl(P params, Processor<? super R> processor);
}
