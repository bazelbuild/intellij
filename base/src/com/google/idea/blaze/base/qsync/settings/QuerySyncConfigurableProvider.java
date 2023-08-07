/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.qsync.settings;

import com.google.common.base.Suppliers;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableProvider;
import java.util.function.Supplier;
import org.jetbrains.annotations.Nullable;

/** A provider of configuration pages that provides the query sync page only if it is enabled. */
public class QuerySyncConfigurableProvider extends ConfigurableProvider {
  public static final Supplier<Boolean> ENABLED =
      Suppliers.memoize(new BoolExperiment("use.query.sync.beta", false)::getValue);

  @Override
  public @Nullable Configurable createConfigurable() {
    return new QuerySyncConfigurable();
  }

  @Override
  public boolean canCreateConfigurable() {
    return ENABLED.get();
  }
}
