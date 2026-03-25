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
package com.google.idea.blaze.base.ideinfo;

import com.google.devtools.intellij.ideinfo.IntellijIdeInfo;

/** Ide info specific to typescript rules. */
public final class TsIdeInfo implements ProtoWrapper<IntellijIdeInfo.TsIdeInfo> {

  private static final TsIdeInfo INSTANCE = new TsIdeInfo();

  private TsIdeInfo() {}

  static TsIdeInfo fromProto(IntellijIdeInfo.TsIdeInfo proto) {
    return INSTANCE;
  }

  @Override
  public IntellijIdeInfo.TsIdeInfo toProto() {
    return IntellijIdeInfo.TsIdeInfo.newBuilder().build();
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Builder for ts rule info */
  public static class Builder {
    public TsIdeInfo build() {
      return INSTANCE;
    }
  }

  @Override
  public String toString() {
    return "TsIdeInfo{}";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    return o != null && getClass() == o.getClass();
  }

  @Override
  public int hashCode() {
    return 0;
  }
}
