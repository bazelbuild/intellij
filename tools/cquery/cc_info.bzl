# cquery script to dump the CcInfo of a target
# usage: bazel cquery '<target>' --output=starlark --starlark:file=tools/cquery/cc_info.bzl

CC_INFO = "CcInfo"

def compilation_context_to_struct(ctx):
    return struct(
        defines = ctx.defines.to_list(),
        direct_headers = ctx.direct_headers,
        direct_private_headers = ctx.direct_private_headers,
        derect_textutal_headers = ctx.direct_textual_headers,
        external_includes = ctx.external_includes.to_list(),
        framework_includes = ctx.framework_includes.to_list(),
        includes = ctx.includes.to_list(),
        local_defines = ctx.local_defines.to_list(),
        quote_includes = ctx.quote_includes.to_list(),
        system_includes = ctx.system_includes.to_list(),
        validation_artifacts = ctx.validation_artifacts.to_list(),
    )

def linker_input_to_struct(input):
    return struct(
        owner = str(input.owner),
        # TODO: missing fields :c
    )

def linking_context_to_struct(ctx):
    return struct(
        linker_inputs = [linker_input_to_struct(it) for it in ctx.linker_inputs.to_list()],
    )

def fomrat_cc_info(info):
    result = struct(
        compilation_context = compilation_context_to_struct(info.compilation_context),
        linking_context = linking_context_to_struct(info.linking_context),
    )

    return "CcInfo {\n%s}" % proto.encode_text(result)

def format(target):
    buffer = "%s: " % target.label

    if CC_INFO in providers(target):
        buffer += fomrat_cc_info(providers(target)[CC_INFO])
    else:
        buffer += "NO CcInfo"

    return buffer
