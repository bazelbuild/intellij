# Copyright 2023 The Cross-Media Measurement Authors
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

"""Module extension for non-module dependencies."""

load(
    "@bazel_tools//tools/build_defs/repo:http.bzl",
    "http_file",
)

_file_version_tag = tag_class(
    attrs = {
        "version": attr.string(),
        "sha256": attr.string(),
    },
)

def _non_module_deps_impl(mctx):
    grpc_java_plugin_version = None
    for mod in mctx.modules:
        for file_version in mod.tags.grpc_java_plugin_version:
            if grpc_java_plugin_version:
                fail("Only one grpc-java protoc plugin version is supported")
            grpc_java_plugin_version = file_version

    http_file(
        name = "protoc_gen_grpc_java",
        url = "https://repo1.maven.org/maven2/io/grpc/protoc-gen-grpc-java/{version}/protoc-gen-grpc-java-{version}-linux-x86_64.exe".format(
            version = grpc_java_plugin_version.version,
        ),
        sha256 = grpc_java_plugin_version.sha256,
        executable = True,
    )

non_module_deps = module_extension(
    implementation = _non_module_deps_impl,
    tag_classes = {
        "grpc_java_plugin_version": _file_version_tag,
    },
)
