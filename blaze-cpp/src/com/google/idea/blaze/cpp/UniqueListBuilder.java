/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.cpp;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;

import java.util.Set;

/**
 * A list where an item is only added if it is not already in the list.
 */
final class UniqueListBuilder<T> {
  private final Set<T> set = Sets.newLinkedHashSet();

  /**
   * Add {@param element} if it is not already in the list.
   * @return true if the element has been added, false otherwise.
   */
  public boolean add(T element) {
    return set.add(element);
  }

  /**
   * For each element in {@param elements} add the element to the list if it is not already in the list.
   */
  public void addAll(Iterable<T> elements) {
    for (T element : elements) {
      add(element);
    }
  }

  public ImmutableList<T> build() {
    return ImmutableList.copyOf(set);
  }
}
