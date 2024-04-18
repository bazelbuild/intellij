"""Aspects to build and collect project's render dependencies."""

load("@rules_java//java:defs.bzl", "JavaInfo")

RenderDependenciesInfo = provider(
    "The render dependencies",
    fields = {
        "jars": "a list of jars generated for project files and external dependencies",
    },
)

def _package_render_dependencies_impl(target, ctx):
    return [OutputGroupInfo(
        jars = target[RenderDependenciesInfo].jars.to_list(),
    )]

package_render_dependencies = aspect(
    implementation = _package_render_dependencies_impl,
    required_aspect_providers = [[RenderDependenciesInfo]],
)

def _collect_render_dependencies_impl(target, ctx):
    if JavaInfo not in target:
        return [RenderDependenciesInfo(
            jars = depset(),
        )]
    return [
        RenderDependenciesInfo(
            jars = depset([], transitive = [target[JavaInfo].transitive_runtime_jars]),
        ),
    ]

collect_render_dependencies = aspect(
    implementation = _collect_render_dependencies_impl,
    provides = [RenderDependenciesInfo],
)
