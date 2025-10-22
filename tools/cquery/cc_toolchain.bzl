# cquery script to dump the CcToolchainInfo of a target
# usage: bazel cquery '<target>' --output=starlark --starlark:file=tools/cquery/cc_toolchain.bzl

BUILTIN_CC_TOOLCHAIN_INFO = "@@_builtins//:common/cc/cc_toolchain_info.bzl%CcToolchainInfo"
RULESET_CC_TOOLCHAIN_INFO = "@@rules_cc+//cc/private/rules_impl:cc_toolchain_info.bzl%CcToolchainInfo"

def get_cc_toolchain_info(target):
    ps = providers(target) or []

    if BUILTIN_CC_TOOLCHAIN_INFO in ps:
        return ps[BUILTIN_CC_TOOLCHAIN_INFO]
    if RULESET_CC_TOOLCHAIN_INFO in ps:
        return ps[RULESET_CC_TOOLCHAIN_INFO]

    return None

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
    info = get_cc_toolchain_info(target)

    if info:
        buffer += format_cc_toolchain(info)
    else:
        buffer += "NO CcTolchainInfo"

    return buffer
