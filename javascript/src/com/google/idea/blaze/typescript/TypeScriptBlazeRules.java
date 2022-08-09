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
package com.google.idea.blaze.typescript;

import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.RuleType;

class TypeScriptBlazeRules implements Kind.Provider {

  @Override
  public ImmutableSet<Kind> getTargetKinds() {
    return ImmutableSet.of(
        Kind.Provider.create("ng_module", LanguageClass.TYPESCRIPT, RuleType.LIBRARY),
        Kind.Provider.create("ts_library", LanguageClass.TYPESCRIPT, RuleType.LIBRARY),
        Kind.Provider.create("ts_declaration", LanguageClass.TYPESCRIPT, RuleType.LIBRARY),
        Kind.Provider.create("ts_config", LanguageClass.TYPESCRIPT, RuleType.BINARY));
  }
}
