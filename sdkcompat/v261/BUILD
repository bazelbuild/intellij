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
    deps = [
        "//intellij_platform_sdk:jsr305",  # unuseddeps: keep
        "//intellij_platform_sdk:plugin_api",
        "//third_party/javascript",  #api252
        "//third_party/python",
    ],
    name = "v261",
    srcs = select_for_ide(
        default = glob([
            "com/google/idea/sdkcompat/base/**",
            "com/google/idea/sdkcompat/ide/**",
            "com/google/idea/sdkcompat/python/**",
            "com/google/idea/sdkcompat/testFramework/**",
        ]),
        intellij_ue = glob([
            "com/google/idea/sdkcompat/base/**",
            "com/google/idea/sdkcompat/ide/**",
            "com/google/idea/sdkcompat/javascript/**",
            "com/google/idea/sdkcompat/python/**",
            "com/google/idea/sdkcompat/testFramework/**",
        ]),
    ),
    visibility = ["//sdkcompat:__pkg__"],
)
