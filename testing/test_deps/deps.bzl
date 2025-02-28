"""
ASwB test workspace and its dependencies setup.
"""

load("@@//testing/test_deps:test_deps_bazel_artifacts.bzl", "ASWB_TEST_DEPS")
load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_file")

def _exec(repository_ctx, working_directory, args):
    execute_result = repository_ctx.execute(args, working_directory = working_directory)
    if execute_result.return_code != 0:
        fail("Failed executing '%s' with %d:\n%s" % (args, execute_result.return_code, execute_result.stderr))
    return execute_result.stdout.splitlines()

def _exec_zip(repository_ctx, source_path, zip_file):
    return _exec(repository_ctx, source_path, ["zip", str(zip_file), "./", "-r0"])

def _aswb_test_projects_repository_impl(repository_ctx):
    workspace_root_path = str(repository_ctx.workspace_root) + "/"
    repo_root = repository_ctx.path("")

    path = repository_ctx.path(workspace_root_path + repository_ctx.attr.path)
    repository_ctx.watch_tree(path)

    _exec_zip(repository_ctx, str(path), str(repo_root) + "/" + "all_sources.zip")
    repository_ctx.template(
        "BUILD.bazel",
        workspace_root_path + "testing/test_deps/aswb_test_projects.BUILD",
    )

aswb_test_projects_repository = repository_rule(
    doc = """
    A repository rule that sets up a local repository derived from [path] by
    renaming all build related files (BUILD, WORKSPACE, etc.) to *._TEST_ and
    supplementing it with a build file that zips the resulting workspace.
    """,
    implementation = _aswb_test_projects_repository_impl,
    local = True,
    attrs = {
        "path": attr.string(),
    },
)

def aswb_test_deps_dependencies():
    """
    Set up the @aswb_test_projects workspace and its dependencies.
    """
    http_file(
        name = "aswb_test_deps_bazel",
        executable = True,
        sha256 = "b774f62102de61f2889784cbe46451b098d22f1553fe366ed0f251602332fa84",
        url = "https://github.com/bazelbuild/bazel/releases/download/7.5.0/bazel-7.5.0-linux-x86_64",
        visibility = ["//:__subpackages__"],
    )

    http_file(
        name = "aswb_test_deps_bazel_central_registry",
        sha256 = "2938e660258a4eb71c1a73135ada13cc1db7482a1699e8903ad23bc5ac2da7ad",
        downloaded_file_path = "bazel_central_registry.zip",
        url = "https://github.com/bazelbuild/bazel-central-registry/archive/167cad116d6456fcb1edd5b67d102d460a6e624b.zip",
        visibility = ["//:__subpackages__"],
    )

    aswb_test_projects_repository(
        name = "aswb_test_projects",
        path = "testing/test_deps/projects",
    )

    for k, v in ASWB_TEST_DEPS.items():
        http_file(
            name = "aswb_test_deps_" + v["name"],
            url = k,
            sha256 = v["sha256"],
            downloaded_file_path = v["name"],
            visibility = ["//:__subpackages__"],
        )
