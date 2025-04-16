"""Blaze/google3 specific dependencies for build_dependencies.bzl """

load(
    "@bazel_tools//tools/build_defs/cc:action_names.bzl",
    _CPP_COMPILE_ACTION_NAME = "CPP_COMPILE_ACTION_NAME",
    _C_COMPILE_ACTION_NAME = "C_COMPILE_ACTION_NAME",
)
load("@rules_java//java:defs.bzl", "JavaInfo")
load("//third_party/protobuf/bazel/common:proto_info.bzl", "ProtoInfo")

# re-export these with their original names:
CPP_COMPILE_ACTION_NAME = _CPP_COMPILE_ACTION_NAME
C_COMPILE_ACTION_NAME = _C_COMPILE_ACTION_NAME

ZIP_TOOL_LABEL = "@@bazel_tools//tools/zip:zipper"

# JAVA

def _get_java_info(target, rule):
    if not JavaInfo in target:
        return None
    p = target[JavaInfo]
    return struct(
        compile_jars = _collect_output_jars(p, rule),
        java_outputs = p.java_outputs,
        transitive_compile_time_jars = p.transitive_compile_time_jars,
        transitive_runtime_jars = p.transitive_runtime_jars,
    )

def _collect_output_jars(java_info, rule):
    java_outputs = []
    if hasattr(java_info, "java_outputs") and java_info.java_outputs:
        java_outputs = java_info.java_outputs
    ijars = [jar.ijar for jar in java_outputs if jar.ijar]
    output_jars = depset(direct = ijars)

    # use compile_jar for *proto_library as it's only a wrapper point to the
    # proto_library target, there's no  java_outputs. And proto_library will
    # only provide access to proto file not the jar file. So we can only access
    # those jar files via java_info.compile_jars
    if not output_jars and _is_proto_library_wrapper(rule):
        return java_info.compile_jars
    return output_jars

def _is_proto_library_wrapper(rule):
    if not rule.kind.endswith("proto_library") or rule.kind == "proto_library":
        return False
    deps = _get_dependency_attribute(rule, "deps")
    return len(deps) == 1 and ProtoInfo in deps[0]

IDE_JAVA = struct(
    srcs_attributes = ["java_srcs", "java_test_srcs"],
    get_java_info = _get_java_info,
)

# KOTLIN

_KOTLIN_TOOLCHAIN_TYPE = "//third_party/bazel_rules/rules_kotlin/toolchains/kotlin_jvm"
_CC_TOOLCHAIN_TYPE = "//tools/cpp:toolchain_type"

def _get_dependency_attribute(rule, attr):
    if hasattr(rule.attr, attr):
        to_add = getattr(rule.attr, attr)
        if type(to_add) == "list":
            return [t for t in to_add if type(t) == "Target"]
        elif type(to_add) == "Target":
            return [to_add]
    return []

def _get_followed_kotlin_dependencies(rule):
    deps = []
    if rule.kind in ["kt_jvm_toolchain"]:
        deps.extend(_get_dependency_attribute(rule, "kotlin_libs"))
    if _KOTLIN_TOOLCHAIN_TYPE in rule.toolchains:
        deps.extend([rule.toolchains[_KOTLIN_TOOLCHAIN_TYPE]])
    return deps

def _get_kotlin_info(target, rule):
    if rule.kind in ["kt_jvm_toolchain"]:
        return struct()
    return None

IDE_KOTLIN = struct(
    srcs_attributes = [
        "kotlin_srcs",
        "kotlin_test_srcs",
        "common_srcs",
    ],
    follow_attributes = [],
    follow_additional_attributes = [
        "kotlin_libs",
    ],
    followed_dependencies = _get_followed_kotlin_dependencies,
    toolchains_aspects = [_KOTLIN_TOOLCHAIN_TYPE],
    get_kotlin_info = _get_kotlin_info,
)

# PROTO

_PROTO_TOOLCHAIN_TYPES = [
    "//third_party/protobuf/bazel/private:java_toolchain_type",
    "//third_party/protobuf/bazel/private:javalite_toolchain_type",
    "@rules_java//java/proto:toolchain_type",
    "@rules_java//java/proto:lite_toolchain_type",
]

def _get_java_proto_info(target, rule):
    if rule.kind in ["proto_lang_toolchain", "java_rpc_toolchain"]:
        return struct()
    return None

def _get_followed_java_proto_dependencies(rule):
    deps = []
    if rule.kind in ["proto_lang_toolchain", "java_rpc_toolchain"]:
        deps.extend(_get_dependency_attribute(rule, "runtime"))
    if rule.kind in ["_java_grpc_library", "_java_lite_grpc_library"]:
        deps.extend(_get_dependency_attribute(rule, "_toolchain"))
    for proto_toolchain_type in _PROTO_TOOLCHAIN_TYPES:
        if proto_toolchain_type in rule.toolchains:
            deps.extend([rule.toolchains[proto_toolchain_type]])
    return deps

IDE_JAVA_PROTO = struct(
    get_java_proto_info = _get_java_proto_info,
    srcs_attributes = [],
    follow_attributes = ["_toolchain", "runtime"],
    followed_dependencies = _get_followed_java_proto_dependencies,
    toolchains_aspects = [
        "//third_party/protobuf/bazel/private:java_toolchain_type",
        "//third_party/protobuf/bazel/private:javalite_toolchain_type",
        "@rules_java//java/proto:toolchain_type",
        "@rules_java//java/proto:lite_toolchain_type",
    ],
)

# CC

def _get_cc_toolchain_target(rule):
    if _CC_TOOLCHAIN_TYPE in rule.toolchains:
        return rule.toolchains[_CC_TOOLCHAIN_TYPE]
    return None

IDE_CC = struct(
    c_compile_action_name = _C_COMPILE_ACTION_NAME,
    cpp_compile_action_name = _CPP_COMPILE_ACTION_NAME,
    follow_attributes = [],
    toolchains_aspects = [_CC_TOOLCHAIN_TYPE],
    toolchain_target = _get_cc_toolchain_target,
)
