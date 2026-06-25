/*
 * Copyright 2026 The Bazel Authors. All rights reserved.
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
package com.google.idea.sdkcompat.javascript;

import com.intellij.lang.typescript.tsconfig.TypeScriptConfig;

/**
 * Bridges {@link TypeScriptConfig}'s strictness checks across SDK versions.
 *
 * <p>#api261: breaking API change with 261.1 
 */
public abstract class BlazeTypeScriptConfigCompat implements TypeScriptConfig {

  protected abstract boolean noImplicitAnyImpl();

  protected abstract boolean noImplicitThisImpl();

  protected abstract boolean strictNullChecksImpl();

  protected abstract boolean strictBindCallApplyImpl();

  protected abstract LanguageTarget getLanguageTargetImpl();

  @Override
  public final boolean noImplicitAny(boolean ts6orNewer) {
    return noImplicitAnyImpl();
  }

  @Override
  public final boolean noImplicitThis(boolean ts6orNewer) {
    return noImplicitThisImpl();
  }

  @Override
  public final boolean strictNullChecks(boolean ts6orNewer) {
    return strictNullChecksImpl();
  }

  @Override
  public final boolean strictBindCallApply(boolean ts6orNewer) {
    return strictBindCallApplyImpl();
  }

  @Override
  public LanguageTarget getLanguageTarget(boolean ts6orNewer) {
    return getLanguageTargetImpl();
  }
}
