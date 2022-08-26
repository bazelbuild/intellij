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
package com.google.idea.blaze.rust;

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.RuleType;
import java.util.Arrays;

/** Contributes Rust rules to {@link Kind}. */
public final class RustBlazeRules implements Kind.Provider {

    /** Rust-specific blaze rules. */
    public enum RuleTypes {
        RUST_BINARY("rust_binary", LanguageClass.RUST, RuleType.BINARY),
        RUST_LIBRARY("rust_library", LanguageClass.RUST, RuleType.LIBRARY),
        RUST_PROC_MACRO("rust_proc_macro", LanguageClass.RUST, RuleType.LIBRARY),
        RUST_TEST("rust_test", LanguageClass.RUST, RuleType.TEST);

        private final String name;
        private final LanguageClass languageClass;
        private final RuleType ruleType;

        RuleTypes(String name, LanguageClass languageClass, RuleType ruleType) {
            this.name = name;
            this.languageClass = languageClass;
            this.ruleType = ruleType;
        }

        public Kind getKind() {
            return Preconditions.checkNotNull(Kind.fromRuleName(name));
        }
    }

    @Override
    public ImmutableSet<Kind> getTargetKinds() {
        return Arrays.stream(RuleTypes.values())
                .map(e -> Kind.Provider.create(e.name, e.languageClass, e.ruleType))
                .collect(toImmutableSet());
    }
}
