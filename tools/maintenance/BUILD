load("@rules_java//java:defs.bzl", "java_binary")

# Copyright 2024 The Bazel Authors. All rights reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
load("@rules_kotlin//kotlin:jvm.bzl", "kt_jvm_library")

kt_jvm_library(
    name = "lib",
    srcs = ["maintenance.kt"],
    deps = [
        "@maven//:com_google_guava_guava",
        "@maven//:org_jetbrains_kotlin_kotlin_stdlib"
    ],
)

java_binary(
    name = "maintenance",
    main_class = "MaintenanceKt",
    runtime_deps = [":lib"],
)
