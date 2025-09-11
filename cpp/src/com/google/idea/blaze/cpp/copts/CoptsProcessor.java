/*
 * Copyright 2025 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.cpp.copts;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.sync.workspace.ExecutionRootPathResolver;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.text.Strings;
import com.jetbrains.cidr.lang.workspace.compiler.CompilerSpecificSwitchBuilder;
import com.jetbrains.cidr.lang.workspace.compiler.OCCompilerKind;

public interface CoptsProcessor {

  ExtensionPointName<CoptsProcessor> EP_NAME = new ExtensionPointName<>("com.google.idea.blaze.cpp.copts.CoptsProcessor");

  /**
   * Specialized for filtering the list of copts based on a condition.
   */
  abstract class Filter implements CoptsProcessor {

    /**
     * Whether to drop this specific option. Multiple filters are joined using a disjunction.
     */
    protected abstract boolean drop(String option);

    @Override
    public final ImmutableList<String> process(
        ImmutableList<String> options,
        OCCompilerKind kind,
        CompilerSpecificSwitchBuilder sink,
        ExecutionRootPathResolver resolver
    ) {
      return options.stream().filter((it) -> !drop(it)).collect(ImmutableList.toImmutableList());
    }
  }

  /**
   * Specialized for transforming one specific flag in the list of copts.
   */
  abstract class Transform implements CoptsProcessor {

    /**
     * Which flag to transform. If the list is empty, this processor is disabled.
     */
    protected abstract ImmutableList<String> flags(OCCompilerKind kind);

    @Override
    public final boolean enabled(OCCompilerKind kind) {
      return !flags(kind).isEmpty();
    }

    /**
     * Process the value of the flag and apply it to the switch builder.
     */
    protected abstract void apply(String value, CompilerSpecificSwitchBuilder sink, ExecutionRootPathResolver resolver);

    @Override
    public final ImmutableList<String> process(
        ImmutableList<String> options,
        OCCompilerKind kind,
        CompilerSpecificSwitchBuilder sink,
        ExecutionRootPathResolver resolver
    ) {
      final var unprocessed = new ImmutableList.Builder<String>();

      final var flags = flags(kind);
      assert !flags.isEmpty();

      var consumeNext = false;
      options: for (final var option : options) {
        if (consumeNext) {
          apply(option, sink, resolver);
          consumeNext = false;
          continue options;
        }

        for (final var flag : flags) {
          if (option.equals(flag)) {
            consumeNext = true;
            continue options;
          }
        }

        for (final var flag : flags) {
          if (option.startsWith(flag)) {
            apply(Strings.trimStart(option.substring(flag.length()), "="), sink, resolver);
            continue options;
          }
        }

        unprocessed.add(option);
      }

      return unprocessed.build();
    }
  }

  /**
   * Whether the transformer is enabled. Normally should just a registry key or compiler kind check.
   */
  boolean enabled(OCCompilerKind kind);

  /**
   * Processes a list of copts into and applies them to the switch builder. Returns all unprocessed options.
   */
  ImmutableList<String> process(
      ImmutableList<String> options,
      OCCompilerKind kind,
      CompilerSpecificSwitchBuilder sink,
      ExecutionRootPathResolver resolver
  );

  static void apply(
      ImmutableList<String> options,
      OCCompilerKind kind,
      CompilerSpecificSwitchBuilder sink,
      ExecutionRootPathResolver resolver
  ) {
    var current = options;
    for (final var processor : EP_NAME.getExtensionList()) {
      if (processor.enabled(kind)) {
        current = processor.process(current, kind, sink, resolver);
      }
    }

    // add all unprocessed options as raw switches
    sink.withSwitches(current);
  }
}
