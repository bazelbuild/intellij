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
import com.google.common.collect.ComparisonChain;
import com.google.idea.blaze.base.model.primitives.Label;
import java.io.Serializable;
import javax.annotation.Nullable;

/** A key that uniquely idenfifies a target in the target map */
public class TargetKey implements Serializable, Comparable<TargetKey> {
  private static final long serialVersionUID = 2L;

  public final Label label;
  @Nullable private final String aspectId;

  private TargetKey(Label label, @Nullable String aspectId) {
    this.label = label;
    this.aspectId = aspectId;
  }

  /** Returns a key identifying a plain target */
  public static TargetKey forPlainTarget(Label label) {
    return new TargetKey(label, null);
  }

  /** Returns a key identifying a general target */
  public static TargetKey forGeneralTarget(Label label, @Nullable String aspectId) {
    return new TargetKey(label, aspectId);
  }

  public boolean isPlainTarget() {
    return aspectId == null;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    TargetKey key = (TargetKey) o;
    return Objects.equal(label, key.label) && Objects.equal(aspectId, key.aspectId);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(label, aspectId);
  }

  @Override
  public String toString() {
    if (aspectId == null) {
      return label.toString();
    }
    return label.toString() + "#" + aspectId;
  }

  @Override
  public int compareTo(TargetKey o) {
    return ComparisonChain.start().compare(label, o.label).compare(aspectId, o.aspectId).result();
  }
}
