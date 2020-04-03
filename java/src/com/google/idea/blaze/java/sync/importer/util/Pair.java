/*
 * Copyright 2020 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.java.sync.importer.util;

/**
 * Class to simplify syntax when creating a pair of objects of the same type.
 *
 * <p>Rather than using {@code Pair<MyClass, MyClass>} as with {@link
 * com.intellij.openapi.util.Pair}, this class can be used as {@code Pair<MyClass>} without changing
 * other semantics.
 */
public class Pair<T> extends com.intellij.openapi.util.Pair<T, T> {

  public static <T> Pair<T> of(T first, T second) {
    return new Pair<>(first, second);
  }

  protected Pair(T first, T second) {
    super(first, second);
  }
}
