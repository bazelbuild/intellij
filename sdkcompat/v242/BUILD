# Copyright 2024 The Bazel Authors. All rights reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Description: Indirections for SDK changes to the underlying platform library.

load("//intellij_platform_sdk:build_defs.bzl", "select_for_ide")

licenses(["notice"])

java_library(
    deps = [
        "//intellij_platform_sdk:jsr305",  # unuseddeps: keep
        "//intellij_platform_sdk:plugin_api",
        "//third_party/go",  #api231
        "//third_party/javascript",  #api223
        "//third_party/scala",
    ],
    name = "v242",
    srcs = glob([
        "com/google/idea/sdkcompat/editor/**",
        "com/google/idea/sdkcompat/general/**",
        "com/google/idea/sdkcompat/fig/**",
        "com/google/idea/sdkcompat/platform/**",
        "com/google/idea/sdkcompat/psi/**",
        "com/google/idea/sdkcompat/python/**",
        "com/google/idea/sdkcompat/ui/icons/**",
        "com/google/idea/sdkcompat/vfs/**",
    ]) + select_for_ide(
        android_studio = glob([
            "com/google/idea/sdkcompat/cpp/**",
            "com/google/idea/sdkcompat/java/**",
            "com/google/idea/sdkcompat/kotlin/**",
            "com/google/idea/sdkcompat/vcs/**",
        ]) + ["com/google/idea/sdkcompat/refactoring/safedelete/JavaSafeDeleteProcessorCompat.java"],
        clion = glob([
            "com/google/idea/sdkcompat/cpp/**",
            "com/google/idea/sdkcompat/clion/**",  # For classes incompatible with Android Studio
            "com/google/idea/sdkcompat/javascript/**",  #api223
        ]),
        intellij = glob([
            "com/google/idea/sdkcompat/java/**",
            "com/google/idea/sdkcompat/scala/**",
            "com/google/idea/sdkcompat/kotlin/**",
        ]) + ["com/google/idea/sdkcompat/refactoring/safedelete/JavaSafeDeleteProcessorCompat.java"],
        intellij_ue = glob([
            "com/google/idea/sdkcompat/java/**",
            "com/google/idea/sdkcompat/scala/**",
            "com/google/idea/sdkcompat/kotlin/**",
            "com/google/idea/sdkcompat/javascript/**",  #api223
            "com/google/idea/sdkcompat/go/**",  #api231
        ]) + ["com/google/idea/sdkcompat/refactoring/safedelete/JavaSafeDeleteProcessorCompat.java"],
    ),
    visibility = ["//sdkcompat:__pkg__"],
)
