/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.run.testmap;

import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.model.primitives.Kind;
import java.util.Comparator;

public class TargetInfoComparator implements Comparator<TargetInfo> {

  /**
   * Sorts the {@link TargetInfo} objects such that there is a preference to those without the
   * underscore on the name and for those that do actually resolve to a {@link Kind}.
   */

  @Override
  public int compare(TargetInfo o1, TargetInfo o2) {
    Kind kind1 = o1.getKind();
    Kind kind2 = o2.getKind();

    if ((null == kind1) != (null == kind2)) {
      return (null == kind1) ? 1 : -1;
    }

    String targetNameStr1 = o1.getLabel().targetName().toString();
    String targetNameStr2 = o2.getLabel().targetName().toString();

    boolean targetNameLeadingUnderscore1 = targetNameStr1.startsWith("_");
    boolean targetNameLeadingUnderscore2 = targetNameStr2.startsWith("_");

    if (targetNameLeadingUnderscore1 != targetNameLeadingUnderscore2) {
      return targetNameLeadingUnderscore1 ? 1 : -1;
    }

    return targetNameStr1.compareTo(targetNameStr2);
  }

}