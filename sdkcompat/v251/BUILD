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

load("@rules_java//java:java_library.bzl", "java_library")
load("//intellij_platform_sdk:build_defs.bzl", "select_for_ide")

licenses(["notice"])

java_library(
    name = "v251",
    srcs = select_for_ide(
        clion = glob([
            "com/google/idea/sdkcompat/python/**",
            "com/google/idea/sdkcompat/javascript/**",  #api223
        ]),
        intellij_ue = glob([
            "com/google/idea/sdkcompat/python/**",
            "com/google/idea/sdkcompat/javascript/**",  #api223
        ]),
        default = glob([
            "com/google/idea/sdkcompat/python/**",
        ])
    ),
    visibility = ["//sdkcompat:__pkg__"],
    deps = [
        "//intellij_platform_sdk:jsr305",  # unuseddeps: keep
        "//intellij_platform_sdk:plugin_api",
        "//third_party/go",  #api231
        "//third_party/javascript",  #api223
        "//third_party/scala",
    ],
)
