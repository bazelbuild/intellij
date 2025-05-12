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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo;
import com.google.idea.blaze.base.model.primitives.ExecutionRootPath;
import javax.annotation.Nullable;

/** Represents toolchain info from a cc_toolchain or cc_toolchain_suite */
@AutoValue
public abstract class CToolchainIdeInfo implements ProtoWrapper<IntellijIdeInfo.CToolchainIdeInfo> {

  public abstract ImmutableList<String> getCCompilerOptions();

  public abstract ImmutableList<String> getCppCompilerOptions();

  public abstract ImmutableList<ExecutionRootPath> getBuiltInIncludeDirectories();

  public abstract ExecutionRootPath getCCompiler();

  public abstract ExecutionRootPath getCppCompiler();

  public abstract String getTargetName();

  @Nullable
  public abstract ExecutionRootPath getSysroot();


  static CToolchainIdeInfo fromProto(IntellijIdeInfo.CToolchainIdeInfo proto) {
    return CToolchainIdeInfo.builder()
        .setCCompilerOptions(ImmutableList.copyOf(proto.getCOptionList()))
        .setCppCompilerOptions(ImmutableList.copyOf(proto.getCppOptionList()))
        .setBuiltInIncludeDirectories(ProtoWrapper.map(proto.getBuiltInIncludeDirectoryList(), ExecutionRootPath::fromProto))
        .setCCompiler(ExecutionRootPath.fromProto(proto.getCCompiler()))
        .setCppCompiler(ExecutionRootPath.fromProto(proto.getCppCompiler()))
        .setTargetName(proto.getTargetName())
        .setSysroot(ExecutionRootPath.fromNullableProto(proto.getSysroot()))
        .build();
  }

  @Override
  public IntellijIdeInfo.CToolchainIdeInfo toProto() {
    final var builder = IntellijIdeInfo.CToolchainIdeInfo.newBuilder()
        .addAllCOption(getCCompilerOptions())
        .addAllCppOption(getCppCompilerOptions())
        .addAllBuiltInIncludeDirectory(ProtoWrapper.mapToProtos(getBuiltInIncludeDirectories()))
        .setCCompiler(getCCompiler().toProto())
        .setCppCompiler(getCppCompiler().toProto())
        .setTargetName(getTargetName());

    final var sysroot = getSysroot();
    if (sysroot != null) {
      builder.setSysroot(sysroot.toProto());
    }

    return builder.build();
  }

  public static Builder builder() {
    return new AutoValue_CToolchainIdeInfo.Builder();
  }

  /**
   * Builder for c toolchain
   */
  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setCCompilerOptions(ImmutableList<String> value);

    public abstract Builder setCppCompilerOptions(ImmutableList<String> value);

    public abstract Builder setBuiltInIncludeDirectories(ImmutableList<ExecutionRootPath> value);

    public abstract Builder setCCompiler(ExecutionRootPath value);

    public abstract Builder setCppCompiler(ExecutionRootPath value);

    public abstract Builder setTargetName(String value);

    public abstract Builder setSysroot(@Nullable ExecutionRootPath value);

    public abstract CToolchainIdeInfo build();
  }
}
