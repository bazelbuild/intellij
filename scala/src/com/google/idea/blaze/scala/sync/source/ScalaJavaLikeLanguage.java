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
package com.google.idea.blaze.scala.sync.source;

import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.java.sync.source.JavaLikeLanguage;

/** Provides Java-like parts of Scala to the Java plugin. */
public class ScalaJavaLikeLanguage implements JavaLikeLanguage {
  @Override
  public LanguageClass getLanguageClass() {
    return LanguageClass.SCALA;
  }

  @Override
  public ImmutableSet<String> getFileExtensions() {
    return ImmutableSet.of(".scala");
  }

  @Override
  public ImmutableSet<Kind> getDebuggableKinds() {
    return ImmutableSet.of(Kind.SCALA_BINARY, Kind.SCALA_TEST, Kind.SCALA_JUNIT_TEST);
  }

  @Override
  public ImmutableSet<Kind> getHandledTestKinds() {
    return ImmutableSet.of(Kind.SCALA_TEST);
  }

  @Override
  public ImmutableSet<Kind> getNonSourceKinds() {
    return ImmutableSet.of(Kind.SCALA_IMPORT);
  }
}
