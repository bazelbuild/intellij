/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.common;

import com.google.auto.value.AutoValue;
import java.util.Optional;

/** Represents a build target */
@AutoValue
public abstract class BuildTarget {
  public abstract Label label();

  public abstract String kind();

  public abstract Optional<Label> testApp();

  public abstract Optional<Label> instruments();

  public abstract Optional<String> customPackage();

  public static Builder builder() {
    return new AutoValue_BuildTarget.Builder();
  }

  /** Builder class for {@link BuildTarget} */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setLabel(Label label);

    public abstract Builder setKind(String kind);

    public abstract Builder setTestApp(Label testApp);

    public abstract Builder setInstruments(Label instruments);

    public abstract Builder setCustomPackage(String customPackage);

    public abstract BuildTarget build();
  }
}
