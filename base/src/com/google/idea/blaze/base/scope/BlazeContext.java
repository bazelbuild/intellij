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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import java.util.List;
import javax.annotation.Nullable;

/** Scoped operation context. */
public class BlazeContext {
  @Nullable private BlazeContext parentContext;

  private final List<BlazeScope> scopes = Lists.newArrayList();

  private final ListMultimap<Class<? extends Output>, OutputSink<?>> outputSinks =
      ArrayListMultimap.create();

  private boolean isEnding;
  private boolean isCancelled;
  private int holdCount;
  private boolean hasErrors;
  private boolean propagatesErrors = true;

  public BlazeContext() {
    this(null);
  }

  public BlazeContext(@Nullable BlazeContext parentContext) {
    this.parentContext = parentContext;
  }

  public BlazeContext push(BlazeScope scope) {
    scopes.add(scope);
    scope.onScopeBegin(this);
    return this;
  }

  /** Ends the context scope. */
  public void endScope() {
    if (isEnding || holdCount > 0) {
      return;
    }
    isEnding = true;
    for (int i = scopes.size() - 1; i >= 0; i--) {
      scopes.get(i).onScopeEnd(this);
    }

    if (parentContext != null && hasErrors && propagatesErrors) {
      parentContext.setHasError();
    }
  }

  /**
   * Requests cancellation of the operation.
   *
   * <p>Each context holder must handle cancellation individually.
   */
  public void setCancelled() {
    if (isEnding || isCancelled) {
      return;
    }

    isCancelled = true;

    if (parentContext != null) {
      parentContext.setCancelled();
    }
  }

  public void hold() {
    ++holdCount;
  }

  public void release() {
    if (--holdCount == 0) {
      endScope();
    }
  }

  public boolean isEnding() {
    return isEnding;
  }

  public boolean isCancelled() {
    return isCancelled;
  }

  @Nullable
  public <T extends BlazeScope> T getScope(Class<T> scopeClass) {
    return getScope(scopeClass, scopes.size());
  }

  @Nullable
  private <T extends BlazeScope> T getScope(Class<T> scopeClass, int endIndex) {
    for (int i = endIndex - 1; i >= 0; i--) {
      if (scopes.get(i).getClass() == scopeClass) {
        return scopeClass.cast(scopes.get(i));
      }
    }
    if (parentContext != null) {
      return parentContext.getScope(scopeClass);
    }
    return null;
  }

  @Nullable
  public <T extends BlazeScope> T getParentScope(T scope) {
    int index = scopes.indexOf(scope);
    if (index == -1) {
      throw new IllegalArgumentException("Scope does not belong to this context.");
    }
    @SuppressWarnings("unchecked")
    Class<T> scopeClass = (Class<T>) scope.getClass();
    return getScope(scopeClass, index);
  }

  /**
   * Find all instances of {@param scopeClass} that are on the stack starting with this context.
   * That includes this context and all parent contexts recursively.
   *
   * @param scopeClass type of scopes to locate
   * @return The ordered list of all scopes of type {@param scopeClass}, ordered from {@param
   *     startingScope} to the root.
   */
  @VisibleForTesting
  <T extends BlazeScope> List<T> getScopes(Class<T> scopeClass) {
    List<T> scopesCollector = Lists.newArrayList();
    getScopes(scopesCollector, scopeClass, scopes.size());
    return scopesCollector;
  }

  /**
   * Find all instances of {@param scopeClass} that are above {@param startingScope} on the stack.
   * That includes this context and all parent contexts recursively. {@param startingScope} must be
   * in the this {@link BlazeContext}.
   *
   * @param scopeClass type of scopes to locate
   * @param startingScope scope to start our search from
   * @return If {@param startingScope} is in this context, the ordered list of all scopes of type
   *     {@param scopeClass}, ordered from {@param startingScope} to the root. Otherwise, an empty
   *     list.
   */
  @VisibleForTesting
  <T extends BlazeScope> List<T> getScopes(Class<T> scopeClass, BlazeScope startingScope) {
    List<T> scopesCollector = Lists.newArrayList();
    int index = scopes.indexOf(startingScope);
    if (index == -1) {
      return scopesCollector;
    }

    // index + 1 so we include startingScope
    getScopes(scopesCollector, scopeClass, index + 1);
    return scopesCollector;
  }

  /** Add matching scopes to {@param scopesCollector}. Search from {@param maxIndex} - 1 to 0. */
  @SuppressWarnings("unchecked") // Instanceof check is right before cast.
  @VisibleForTesting
  <T extends BlazeScope> void getScopes(
      List<T> scopesCollector, Class<T> scopeClass, int maxIndex) {
    for (int i = maxIndex - 1; i >= 0; --i) {
      BlazeScope scope = scopes.get(i);
      if (scope.getClass() == scopeClass) {
        scopesCollector.add((T) scope);
      }
    }
    if (parentContext != null) {
      parentContext.getScopes(scopesCollector, scopeClass, parentContext.scopes.size());
    }
  }

  public <T extends Output> BlazeContext addOutputSink(
      Class<T> outputClass, OutputSink<T> outputSink) {
    outputSinks.put(outputClass, outputSink);
    return this;
  }

  /** Produces output by sending it to any registered sinks. */
  @SuppressWarnings("unchecked")
  public synchronized <T extends Output> void output(T output) {
    Class<? extends Output> outputClass = output.getClass();
    List<OutputSink<?>> outputSinks = this.outputSinks.get(outputClass);

    boolean continuePropagation = true;
    for (int i = outputSinks.size() - 1; i >= 0; --i) {
      OutputSink<?> outputSink = outputSinks.get(i);
      OutputSink.Propagation propagation = ((OutputSink<T>) outputSink).onOutput(output);
      continuePropagation = propagation == OutputSink.Propagation.Continue;
      if (!continuePropagation) {
        break;
      }
    }
    if (continuePropagation && parentContext != null) {
      parentContext.output(output);
    }
  }

  /**
   * Sets the error state.
   *
   * <p>The error state will be propagated to any parents.
   */
  public void setHasError() {
    this.hasErrors = true;
  }

  /** Returns true if there were errors */
  public boolean hasErrors() {
    return hasErrors;
  }

  public boolean isRoot() {
    return parentContext == null;
  }

  /** Returns true if no errors and isn't cancelled. */
  public boolean shouldContinue() {
    return !hasErrors() && !isCancelled();
  }

  /** Sets whether errors are propagated to the parent context. */
  public void setPropagatesErrors(boolean propagatesErrors) {
    this.propagatesErrors = propagatesErrors;
  }
}
