/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.ideinfo;

import com.google.common.base.Objects;
import java.io.Serializable;

/** Represents a dependency between two targets. */
public class Dependency implements Serializable {
  private static final long serialVersionUID = 1L;

  /** Type of dependency */
  public enum DependencyType {
    COMPILE_TIME,
    RUNTIME
  }

  private final TargetKey targetKey;
  private final DependencyType dependencyType;

  public Dependency(TargetKey targetKey, DependencyType dependencyType) {
    this.targetKey = targetKey;
    this.dependencyType = dependencyType;
  }

  public TargetKey getTargetKey() {
    return targetKey;
  }

  public DependencyType getDependencyType() {
    return dependencyType;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Dependency that = (Dependency) o;
    return Objects.equal(getTargetKey(), that.getTargetKey())
        && getDependencyType() == that.getDependencyType();
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(getTargetKey(), getDependencyType());
  }
}
