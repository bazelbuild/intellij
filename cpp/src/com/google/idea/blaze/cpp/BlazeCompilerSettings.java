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
package com.google.idea.blaze.cpp;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.base.model.primitives.ExecutionRootPath;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.cidr.lang.CLanguageKind;
import com.jetbrains.cidr.lang.OCLanguageKind;
import com.jetbrains.cidr.lang.workspace.compiler.ClangClCompilerKind;
import com.jetbrains.cidr.lang.workspace.compiler.ClangClSwitchBuilder;
import com.jetbrains.cidr.lang.workspace.compiler.ClangCompilerKind;
import com.jetbrains.cidr.lang.workspace.compiler.ClangSwitchBuilder;
import com.jetbrains.cidr.lang.workspace.compiler.CompilerSpecificSwitchBuilder;
import com.jetbrains.cidr.lang.workspace.compiler.GCCCompilerKind;
import com.jetbrains.cidr.lang.workspace.compiler.GCCSwitchBuilder;
import com.jetbrains.cidr.lang.workspace.compiler.MSVCCompilerKind;
import com.jetbrains.cidr.lang.workspace.compiler.MSVCSwitchBuilder;
import com.jetbrains.cidr.lang.workspace.compiler.OCCompilerKind;
import com.jetbrains.cidr.lang.workspace.compiler.UnknownCompilerKind;
import java.io.File;
import java.util.function.Supplier;
import javax.annotation.Nullable;

@AutoValue
public abstract class BlazeCompilerSettings {

  public abstract @Nullable File cCompiler();

  public abstract @Nullable File cppCompiler();

  public abstract ImmutableList<String> cSwitches();

  public abstract ImmutableList<String> cppSwitches();

  public abstract String version();

  public abstract String name();

  public abstract ImmutableMap<String, String> environment();

  public abstract ImmutableList<ExecutionRootPath> builtInIncludes();

  private <T> T when(Supplier<T> msvc, Supplier<T> clang, Supplier<T> clangCl, Supplier<T> gcc) {
    if (CompilerVersionUtil.isMSVC(version())) {
      return msvc.get();
    }

    if (CompilerVersionUtil.isClang(version())) {
      if (name().endsWith("-cl")) {
        return clangCl.get();
      } else {
        return clang.get();
      }
    }

    // default to gcc
    return gcc.get();
  }

  public OCCompilerKind getCompiler(OCLanguageKind languageKind) {
    if (languageKind != CLanguageKind.C && languageKind != CLanguageKind.CPP) {
      return UnknownCompilerKind.INSTANCE;
    }

    return when(
        /* msvc */ () -> MSVCCompilerKind.INSTANCE,
        /* clang */ () -> ClangCompilerKind.INSTANCE,
        /* clangCl */ () -> ClangClCompilerKind.INSTANCE,
        /* gcc */ () -> GCCCompilerKind.INSTANCE
    );
  }

  public CompilerSpecificSwitchBuilder createSwitchBuilder() {
    return when(
        /* msvc */ MSVCSwitchBuilder::new,
        /* clang */ ClangSwitchBuilder::new,
        /* clangCl */ ClangClSwitchBuilder::new,
        /* gcc */ GCCSwitchBuilder::new
    );
  }

  public File getCompilerExecutable(OCLanguageKind lang) {
    if (lang == CLanguageKind.C) {
      return cCompiler();
    } else if (lang == CLanguageKind.CPP) {
      return cppCompiler();
    }
    // We don't support objective c/c++.
    return null;
  }

  public ImmutableList<String> getCompilerSwitches(OCLanguageKind lang, @Nullable VirtualFile sourceFile) {
    if (lang == CLanguageKind.C) {
      return cSwitches();
    }
    if (lang == CLanguageKind.CPP) {
      return cppSwitches();
    }
    return ImmutableList.of();
  }

  public static Builder builder() {
    return new AutoValue_BlazeCompilerSettings.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setCCompiler(@Nullable File value);

    public abstract Builder setCppCompiler(@Nullable File value);

    public abstract Builder setCSwitches(ImmutableList<String> value);

    public abstract Builder setCppSwitches(ImmutableList<String> value);

    public abstract Builder setVersion(String value);

    public abstract Builder setName(String value);

    public abstract Builder setEnvironment(ImmutableMap<String, String> value);

    public abstract Builder setBuiltInIncludes(ImmutableList<ExecutionRootPath> value);

    public abstract BlazeCompilerSettings build();
  }
}
