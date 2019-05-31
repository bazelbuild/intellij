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
package com.google.idea.blaze.javascript;

import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.RuleType;

class JavascriptBlazeRules implements Kind.Provider {

  @Override
  public ImmutableSet<Kind> getTargetKinds() {
    return ImmutableSet.of(
        Kind.Provider.create("js_binary", LanguageClass.JAVASCRIPT, RuleType.BINARY),
        Kind.Provider.create("js_module_binary", LanguageClass.JAVASCRIPT, RuleType.BINARY),
        Kind.Provider.create("js_library", LanguageClass.JAVASCRIPT, RuleType.LIBRARY),
        Kind.Provider.create("js_puppet_test", LanguageClass.JAVASCRIPT, RuleType.TEST),
        Kind.Provider.create("jsunit_test", LanguageClass.JAVASCRIPT, RuleType.TEST),
        Kind.Provider.create("jspb_proto_library", LanguageClass.JAVASCRIPT, RuleType.LIBRARY),
        Kind.Provider.create("pinto_library", LanguageClass.JAVASCRIPT, RuleType.LIBRARY),
        Kind.Provider.create("pinto_library_mod", LanguageClass.JAVASCRIPT, RuleType.LIBRARY),
        Kind.Provider.create("pinto_module", LanguageClass.JAVASCRIPT, RuleType.UNKNOWN),
        Kind.Provider.create("_nodejs_binary", LanguageClass.JAVASCRIPT, RuleType.BINARY),
        Kind.Provider.create("_nodejs_module", LanguageClass.JAVASCRIPT, RuleType.LIBRARY),
        Kind.Provider.create("_nodejs_test", LanguageClass.JAVASCRIPT, RuleType.TEST),
        // not executable, despite the name
        Kind.Provider.create(
            "checkable_js_lib_binary", LanguageClass.JAVASCRIPT, RuleType.LIBRARY));
  }
}
