/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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

import com.google.common.base.Strings;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo;
import com.google.idea.blaze.base.model.primitives.Label;
import java.util.Objects;
import javax.annotation.Nullable;

/** Ide info specific to android instrumentation tests */
public class AndroidInstrumentationInfo
    implements ProtoWrapper<IntellijIdeInfo.AndroidInstrumentationInfo> {

  @Nullable
  public Label getTestApp() {
    return testApp;
  }

  @Nullable private final Label testApp;

  private AndroidInstrumentationInfo(@Nullable Label testApp) {
    this.testApp = testApp;
  }

  static AndroidInstrumentationInfo fromProto(IntellijIdeInfo.AndroidInstrumentationInfo proto) {
    return new AndroidInstrumentationInfo(
        !Strings.isNullOrEmpty(proto.getTestApp()) ? Label.create(proto.getTestApp()) : null);
  }

  @Override
  public IntellijIdeInfo.AndroidInstrumentationInfo toProto() {
    IntellijIdeInfo.AndroidInstrumentationInfo.Builder builder =
        IntellijIdeInfo.AndroidInstrumentationInfo.newBuilder();
    ProtoWrapper.unwrapAndSetIfNotNull(builder::setTestApp, testApp);
    return builder.build();
  }

  /** Builder for android instrumentation test rule */
  public static class Builder {
    private Label testApp;

    public Builder setTestApp(Label testApp) {
      this.testApp = testApp;
      return this;
    }

    public AndroidInstrumentationInfo build() {
      return new AndroidInstrumentationInfo(testApp);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof Builder)) {
        return false;
      }
      if (!(o instanceof AndroidInstrumentationInfo)) {
        return false;
      }
      AndroidInstrumentationInfo that = (AndroidInstrumentationInfo) o;
      return testApp.equals(that.getTestApp());
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(testApp);
    }
  }
}
