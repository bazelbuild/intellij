"""Bazel-specific intellij aspect."""

load(
    ":intellij_info_impl.bzl",
    "create_code_generator_rule_name_aspect_attr_name",
    "intellij_info_aspect_impl",
    "make_intellij_info_aspect",
)

EXTRA_DEPS = [
    "embed",  # From go rules (bazel only)
]

def tool_label(tool_name):
    """Returns a label that points to a tool target in the bundled aspect workspace."""
    return Label("//aspect/tools:" + tool_name)

def get_go_import_path(ctx):
    """Returns the import path for a go target."""
    import_path = getattr(ctx.rule.attr, "importpath", None)
    if import_path:
        return import_path
    prefix = None
    if hasattr(ctx.rule.attr, "_go_prefix"):
        prefix = ctx.rule.attr._go_prefix.go_prefix
    if not prefix:
        return None
    import_path = prefix
    if ctx.label.package:
        import_path += "/" + ctx.label.package
    if ctx.label.name != "go_default_library":
        import_path += "/" + ctx.label.name
    return import_path

def is_go_proto_library(target, _ctx):
    return hasattr(target[OutputGroupInfo], "go_generated_srcs")

def get_go_proto_library_generated_srcs(target):
    files = target[OutputGroupInfo].go_generated_srcs.to_list()
    return [f for f in files if f.basename.endswith(".go")]

def get_py_launcher(target, ctx):
    """Returns the python launcher for a given rule."""

    # Used by other implementations of get_launcher
    _ = target  # @unused
    attr = ctx.rule.attr
    if hasattr(attr, "_launcher") and attr._launcher != None:
        return str(attr._launcher.label)
    return None

def create_code_generator_rule_name_aspect_attrs(language_names):
    """Create attrs defining rule names that are code-generator for a language

    The Aspect may allow for one or more languages to specify Rules' names that indicate that the
    rule output is source for that language; these Rules should be considered to be code-generators.
    This function will produce a `dict` of attributes that can be configured on the aspect to
    allow the rule names to be configured. See the plugin class `AspectStrategy` to understand this
    from the plugin side.
    """

    def create_attr(language_name):
        return attr.string(
            default = "",
            doc = """This is a set of rule names that are interpreted as being code-generators for the
                 {language_name} language. The string should be a comma-separated list of rule names
                 with no spaces.""".format(language_name = language_name),
        )

    return {
        create_code_generator_rule_name_aspect_attr_name(ln): create_attr(ln)
        for ln in language_names
    }

semantics = struct(
    tool_label = tool_label,
    extra_deps = EXTRA_DEPS,
    extra_required_aspect_providers = [],
    go = struct(
        get_import_path = get_go_import_path,
        is_proto_library = is_go_proto_library,
        get_proto_library_generated_srcs = get_go_proto_library_generated_srcs,
    ),
    py = struct(
        get_launcher = get_py_launcher,
    ),
    flag_hack_label = "//aspect:flag_hack",
    attrs = create_code_generator_rule_name_aspect_attrs(["python"]),
)

def _aspect_impl(target, ctx):
    return intellij_info_aspect_impl(target, ctx, semantics)

intellij_info_aspect = make_intellij_info_aspect(_aspect_impl, semantics)
