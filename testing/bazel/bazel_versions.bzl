_BAZEL_RELEASE_URL = "https://github.com/bazelbuild/bazel/releases/download/{version}/bazel-{version}-{os}-{arch}"

_BUILD_FILE_HEADER = """
load("@@//testing/bazel:bazel_binary.bzl", "bazel_binary")

package(default_visibility = ["//visibility:public"])
"""

_BUILD_FILE_BINARY = """
bazel_binary(
    name = "{name}",
    version = "{version}",
    executable = "{executable}",
)
"""

def _os_name(rctx):
    name = rctx.os.name.lower()
    if name.startswith("linux"):
        return "linux"
    if name.startswith("mac os"):
        return "darwin"
    if name.startswith("windows"):
        return "windows"
    fail("unrecognized os: %s" % name)

def _arch_name(rctx):
    arch = rctx.os.arch.lower()
    if arch.startswith("amd64") or arch.startswith("x86_64"):
        return "x86_64"
    if arch.startswith("aarch64") or arch.startswith("arm"):
        return "arm64"
    fail("unrecognized arch: %s" % arch)

def _format_version_label(version):
    return version.replace(".", "_")

def _bazel_versions_impl(rctx):
    content = _BUILD_FILE_HEADER
    os = _os_name(rctx)
    arch = _arch_name(rctx)
    suffix = ".exe" if os == "windows" else ""

    for version in rctx.attr.versions:
        name = _format_version_label(version)
        executable = "%s_bin%s" % (name, suffix)

        url = _BAZEL_RELEASE_URL.format(
            version = version,
            os = os,
            arch = arch,
        ) + suffix

        rctx.download(
            url = url,
            output = executable,
            executable = True,
        )

        content += _BUILD_FILE_BINARY.format(
            name = name,
            version = version,
            executable = executable,
        )

    rctx.file("BUILD", content)

    # Generate versions.bzl so macros can load version metadata
    versions_entries = []
    for version in rctx.attr.versions:
        major = int(version.split(".")[0])
        label = _format_version_label(version)
        versions_entries.append(
            '    %d: struct(version = "%s", label = "@bazel_versions//:%s")' % (major, version, label),
        )

    rctx.template(
        "versions.bzl",
        Label(":versions.bzl.tpl"),
        substitutions = {"%{versions_entries}": ",\n".join(versions_entries)},
    )

bazel_versions = repository_rule(
    implementation = _bazel_versions_impl,
    attrs = {"versions": attr.string_list(mandatory = True)},
    doc = "Downloads multiple Bazel versions for local execution.",
)
