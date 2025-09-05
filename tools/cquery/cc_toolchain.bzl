# cquery script to dump the CcToolchainInfo of a target
# usage: bazel cquery '<target>' --output=starlark --starlark:file=tools/cquery/cc_toolchain.bzl

CC_TOOLCHAIN_INFO = "@@_builtins//:common/cc/cc_toolchain_info.bzl%CcToolchainInfo"

def format_cc_toolchain(toolchain):
    result = struct(
        compiler = toolchain.compiler,
        compiler_executable = toolchain.compiler_executable,
        cpu = toolchain.cpu,
        libc = toolchain.libc,
        built_in_include_directories = toolchain.built_in_include_directories,
        sysroot = toolchain.sysroot or "None",
    )

    return "CcToolchainInfo {\n%s}" % proto.encode_text(result)

def format(target):
    buffer = "%s: " % target.label

    if CC_TOOLCHAIN_INFO in (providers(target) or []):
        buffer += format_cc_toolchain(providers(target)[CC_TOOLCHAIN_INFO])
    else:
        buffer += "NO CcTolchainInfo"

    return buffer
