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
import com.google.idea.blaze.base.model.primitives.Label;
import java.io.Serializable;
import java.util.Comparator;

/** A key that uniquely idenfifies a rule in the rule map */
public class RuleKey implements Serializable, Comparable<RuleKey> {
  private static final long serialVersionUID = 1L;
  public static final Comparator<RuleKey> COMPARATOR =
      (o1, o2) -> String.CASE_INSENSITIVE_ORDER.compare(o1.label.toString(), o2.label.toString());

  public final Label label;

  private RuleKey(Label label) {
    this.label = label;
  }

  /** Returns a key identifying dep for a dependency rule -> dep */
  public static RuleKey forDependency(RuleIdeInfo rule, Label dep) {
    return new RuleKey(dep);
  }

  /** Returns a key identifying a plain target */
  public static RuleKey forPlainTarget(Label label) {
    return new RuleKey(label);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    RuleKey key = (RuleKey) o;
    return Objects.equal(label, key.label);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(label);
  }

  @Override
  public String toString() {
    return label.toString();
  }

  @Override
  public int compareTo(RuleKey o) {
    return COMPARATOR.compare(this, o);
  }
}
