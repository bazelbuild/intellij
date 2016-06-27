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

/**
 * Workspace types.
 *
 * <p>If the user doesn't specify a workspace, she gets the highest
 * supported workspace type by enum ordinal.
 */
public enum WorkspaceType {
  INTELLIJ_PLUGIN("intellij_plugin", LanguageClass.JAVA),
  C("c", LanguageClass.C),
  JAVA("java", LanguageClass.JAVA),
  ANDROID_NDK("android_ndk", LanguageClass.ANDROID, LanguageClass.JAVA, LanguageClass.C),
  ANDROID("android", LanguageClass.ANDROID, LanguageClass.JAVA),
  JAVASCRIPT("javascript");

  private final String name;
  private final LanguageClass[] languages;
  WorkspaceType(String name, LanguageClass... languages) {
    this.name = name;
    this.languages = languages;
  }

  public String getName() {
    return name;
  }

  public LanguageClass[] getLanguages() {
    return languages;
  }

  public static WorkspaceType fromString(String name) {
    for (WorkspaceType ruleClass : WorkspaceType.values()) {
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
}
