/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.sdkcompat.javascript;

import com.intellij.lang.javascript.config.JSModuleTarget;

public enum JSModuleTargetWrapper {
    COMMON_JS(JSModuleTarget.COMMON_JS),
    OTHER(JSModuleTarget.OTHER),
    NODENEXT(JSModuleTarget.NODENEXT),
    UNKNOWN(JSModuleTarget.UNKNOWN);

    private final JSModuleTarget value;

    public JSModuleTarget value() {
        return value;
    }

    JSModuleTargetWrapper(JSModuleTarget value) {
        this.value = value;
    }
}
