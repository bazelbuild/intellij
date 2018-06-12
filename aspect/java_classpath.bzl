"""An aspect which extracts the runtime classpath from a java target."""

load(
    ":intellij_info_impl.bzl",
    "artifact_location",
)

def _runtime_classpath_impl(target, ctx):
    """The top level aspect implementation function.

    Args:
      target: Essentially a struct representing a BUILD target.

      ctx: The context object that can be used to access attributes and generate
      outputs and actions.

    Returns:
      A struct with only the output_groups provider.
    """
    ctx = ctx  # unused argument
    return struct(output_groups = {
        "runtime_classpath": _get_runtime_jars(target),
    })

def _get_runtime_jars(target):
    if hasattr(target, "java"):
        return target.java.compilation_info.runtime_classpath
    if java_common.provider in target:
        java_provider = target[java_common.provider]
        return java_provider.transitive_runtime_jars
    return depset()

def _aspect_def(impl):
    return aspect(implementation = impl)

java_classpath_aspect = _aspect_def(_runtime_classpath_impl)
