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

load(
    "//intellij_platform_sdk:build_defs.bzl",
    "select_for_plugin_api",
)

java_library(
    name = "sdkcompat",
    srcs = select_for_plugin_api({
        "clion-2022.3": ["//clwb/sdkcompat/v223"],
        "clion-2023.1": ["//clwb/sdkcompat/v231"],
        "clion-2023.2": ["//clwb/sdkcompat/v232"],
        "clion-2023.3": ["//clwb/sdkcompat/v233"],
        "clion-2024.1": ["//clwb/sdkcompat/v241"],
        "clion-2024.2": ["//clwb/sdkcompat/v242"],
        "clion-2024.3": ["//clwb/sdkcompat/v243"],
    }),
    visibility = ["//clwb:__pkg__"],
    deps = [
        "//base",
        "//intellij_platform_sdk:plugin_api",
    ],
)
