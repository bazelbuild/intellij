# cquery script to dump the CcInfo of a target
# usage: bazel cquery '<target>' --output=starlark --starlark:file=tools/cquery/cc_info.bzl

BUILTIN_CC_INFO = "CcInfo"
RULESET_CC_INFO = "@@rules_cc+//cc/private:cc_info.bzl%CcInfo"

def get_cc_info(target):
    ps = providers(target) or []

    if BUILTIN_CC_INFO in ps:
        return ps[BUILTIN_CC_INFO]
    if RULESET_CC_INFO in ps:
        return ps[RULESET_CC_INFO]

    return None

def file_list_to_paths(files):
    return [getattr(f, "path", f) for f in files]

def compilation_context_to_struct(ctx):
    return struct(
        defines = ctx.defines.to_list(),
        headers = file_list_to_paths(ctx.headers.to_list()),
        direct_headers = file_list_to_paths(ctx.direct_headers),
        direct_private_headers = file_list_to_paths(ctx.direct_private_headers),
        derect_textutal_headers = file_list_to_paths(ctx.direct_textual_headers),
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

def format_cc_info(info):
    result = struct(
        compilation_context = compilation_context_to_struct(info.compilation_context),
        linking_context = linking_context_to_struct(info.linking_context),
    )

    return "CcInfo {\n%s}" % proto.encode_text(result)

def format(target):
    buffer = "%s: " % target.label
    info = get_cc_info(target)

    if info:
        buffer += format_cc_info(info)
    else:
        buffer += "NO CcInfo"

    return buffer
