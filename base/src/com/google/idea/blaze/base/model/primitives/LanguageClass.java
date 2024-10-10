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
package com.google.idea.blaze.base.model.primitives;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.ideinfo.ProtoWrapper;
import com.google.idea.blaze.base.ideinfo.Tags;
import javax.annotation.Nullable;

/** Language classes. */
public enum LanguageClass implements ProtoWrapper<String> {
  GENERIC("generic", ImmutableSet.of(), null),
  C("c", ImmutableSet.of("c", "cc", "cpp", "h", "hh", "hpp"), null),
  JAVA("java", ImmutableSet.of("java"), null),
  ANDROID("android", ImmutableSet.of("aidl"), null),
  JAVASCRIPT("javascript", ImmutableSet.of("js", "applejs"), null),
  TYPESCRIPT("typescript", ImmutableSet.of("ts", "ats"), null),
  DART("dart", ImmutableSet.of("dart"), null),
  GO("go", ImmutableSet.of("go"), null),
  PYTHON("python", ImmutableSet.of("py", "pyw"), Tags.TARGET_TAG_PY_CODE_GENERATOR),
  SCALA("scala", ImmutableSet.of("scala"), null),
  KOTLIN("kotlin", ImmutableSet.of("kt"), null),
  ;

  private static final ImmutableMap<String, LanguageClass> RECOGNIZED_EXTENSIONS =
      extensionToClassMap();

  private static ImmutableMap<String, LanguageClass> extensionToClassMap() {
    ImmutableMap.Builder<String, LanguageClass> result = ImmutableMap.builder();
    for (LanguageClass lang : LanguageClass.values()) {
      for (String ext : lang.recognizedFilenameExtensions) {
        result.put(ext, lang);
      }
    }
    return result.build();
  }

  private final String name;
  private final ImmutableSet<String> recognizedFilenameExtensions;

  /**
   * The {@code codeGeneratorTag} is a tag that may be applied to a Bazel Rule's {@code tag}
   * attribute to signal to the IDE that the Rule's Actions will generate source code. Each
   * language has its own tag for this purpose.
   * @see com.google.idea.blaze.base.sync.SyncProjectTargetsHelper
   */
  private final String codeGeneratorTag;

  LanguageClass(
      String name,
      ImmutableSet<String> recognizedFilenameExtensions,
      String codeGeneratorTag) {
    this.name = name;
    this.recognizedFilenameExtensions = recognizedFilenameExtensions;
    this.codeGeneratorTag = codeGeneratorTag;
  }

  public String getName() {
    return name;
  }

  public String getCodeGeneratorTag() {
    return codeGeneratorTag;
  }

  public static LanguageClass fromString(String name) {
    for (LanguageClass ruleClass : LanguageClass.values()) {
      if (ruleClass.name.equals(name)) {
        return ruleClass;
      }
    }
    return null;
  }

  @Override
  public String toString() {
    return name;
  }

  /** Returns the LanguageClass associated with the given filename extension, if it's recognized. */
  @Nullable
  public static LanguageClass fromExtension(String filenameExtension) {
    return RECOGNIZED_EXTENSIONS.get(filenameExtension);
  }

  @Override
  public String toProto() {
    return name;
  }
}
