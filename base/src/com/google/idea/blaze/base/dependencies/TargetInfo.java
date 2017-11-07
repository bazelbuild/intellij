/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.dependencies;

/** Some minimal data about a blaze target. */
public class TargetInfo {
  public final String name;
  public final String kind;

  public TargetInfo(String name, String kind) {
    this.name = name;
    this.kind = kind;
  }

  @Override
  public String toString() {
    return String.format("%s (%s)", name, kind);
  }
}
