/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.kotlin.sync.source;

import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.java.sync.source.JavaLikeLanguage;

/** Provides Java-like parts of Kotlin to the Java plugin. */
public class BlazeKotlinJavaLikeLanguage implements JavaLikeLanguage {
  @Override
  public LanguageClass getLanguageClass() {
    return LanguageClass.KOTLIN;
  }

  @Override
  public ImmutableSet<String> getFileExtensions() {
    return ImmutableSet.of(".kt");
  }

  @Override
  public ImmutableSet<Kind> getDebuggableKinds() {
    return ImmutableSet.of(Kind.KT_JVM_BINARY, Kind.KT_JVM_TEST);
  }

  @Override
  public ImmutableSet<Kind> getHandledTestKinds() {
    return ImmutableSet.of(Kind.KT_JVM_TEST);
  }

  @Override
  public ImmutableSet<Kind> getNonSourceKinds() {
    return ImmutableSet.of(Kind.KT_JVM_IMPORT, Kind.KOTLIN_STDLIB);
  }
}
