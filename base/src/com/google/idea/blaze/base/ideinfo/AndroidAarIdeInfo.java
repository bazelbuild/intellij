/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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

/** aar_import ide info */
public final class AndroidAarIdeInfo implements ProtoWrapper<IntellijIdeInfo.AndroidAarIdeInfo> {
  private final ArtifactLocation aar;

  public AndroidAarIdeInfo(ArtifactLocation aar) {
    this.aar = aar;
  }

  static AndroidAarIdeInfo fromProto(IntellijIdeInfo.AndroidAarIdeInfo proto) {
    return new AndroidAarIdeInfo(ArtifactLocation.fromProto(proto.getAar()));
  }

  @Override
  public IntellijIdeInfo.AndroidAarIdeInfo toProto() {
    return IntellijIdeInfo.AndroidAarIdeInfo.newBuilder().setAar(aar.toProto()).build();
  }

  public ArtifactLocation getAar() {
    return aar;
  }

  @Override
  public String toString() {
    return "AndroidAarIdeInfo{" + "\n" + "  aar=" + getAar() + "\n" + '}';
  }
}
