"""
The test_project_root macro and rules supporting it.

test_project_root generates a generates a set of targets contributing to the
:integration_test_data rule that produces the testdata directory representing
prebuilt artifacts that are required to simulate the query sync process in
the CI environment. This reflects the following constraints imposed by the CI
and Bazel.

(a) No network access - i.e. no direct access to the source code repository and
    thus no way to run `bazel query` and `bazel build` on projects with real
    dependencies.

(b) No wildcards in list of labels attributes - i.e. test_project_root requires
    all targets in the package to be listed manually. However, because
    `siblings()` function is available in `genquery` rules, this list can be
    verified and build fails if any targets that matter are missing from the
    list

(c) Wildcards except `siblings()` function are not supported in `genquery`
    rules - .i.e. //package/path/... queries run by the query sync need to be
    replaced with  manually expanded lists of targets

(d) Aspects used from rules cannot have string attributes - means
    collect_depdencies aspect is replaced with collect_all_depdencies aspect
    sharing the same implementation function but applying include/exclude
    filers. Instead, filtering is applied when artifact_info_file are loaded
    by the query sync integration test framework.

The test data directory consists of the following major groups of files.

(1) sources - includes all source files in a test project including any
    subpackages that also need to be described with test_project_root. Note:
    build fails if such a description is missing.

(2) prebuilt dependencies - outputs of the collect_depdencies aspect for all
    targets and their dependencies in this package. Note that unlike queries
    that run in the production, `genquery`ies cannot contain wildcards and
    therefore include/exclude filters are not applied at this stage. However,
    this is not different form bazle populating bazel-out with this files during
    normal build. When artifact_info_file files are loaded they are properly
    filtered to include only the required artifacts.

(3) artifact_info_file files - outputs of package_dependencies aspect for all
    targets and their dependencies in this package. Unlike the query sync in the
    production include /exclude filters are not applied at this stage.
    artifact_info_file are filtered when loaded by the query sync integration
    test rules.

"""

load(
    "//aspect:build_dependencies.bzl",
    "DependenciesInfo",
    "collect_all_dependencies",
    "package_dependencies",
)

def _test_build_deps_impl(ctx):
    f = []
    for t in ctx.attr.deps:
        f += t[DependenciesInfo].compile_time_jars.to_list()
    return [DefaultInfo(
        files = depset(f),
    )]

def _test_build_dep_desc_impl(ctx):
    f = []
    for t in ctx.attr.deps:
        f += t[OutputGroupInfo].artifact_info_file.to_list()
    artifact_info_file = ctx.actions.declare_file("bindir")
    f.append(artifact_info_file)
    ctx.actions.write(
        artifact_info_file,
        ctx.bin_dir.path,
    )
    return [DefaultInfo(
        files = depset(f),
    )]

test_build_deps = rule(
    doc = """
    A rule that build dependencies in the same way that the query sync does.

    The rule applies collect_dependencies aspect to its dependencies and exposes
    collected dependencies as the default output group so that it can be used as
    test data in query sync integration tests.
    """,
    implementation = _test_build_deps_impl,
    attrs = {
        "deps": attr.label_list(aspects = [collect_all_dependencies]),
    },
)

test_build_deps_desc = rule(
    doc = """
    A rule that describes its dependencies as the build dependency phase of the
    query sync does.

    The rule applies collect_dependencies and package_dependencies aspects to
    its dependencies and exposes generated artifact_info_file files as its
    default output group so that it can be used as test data in query sync
    integration tests.
    """,
    implementation = _test_build_dep_desc_impl,
    attrs = {
        "deps": attr.label_list(aspects = [collect_all_dependencies, package_dependencies]),
    },
)

def test_project_root(all_targets, name = ""):
    """
    A macro to turn the package into a test project.

    The macro generates rules that turn the current package into a test project
    for sync integration tests.

    Args:
        name: ignored
        all_targets: the labels of all targets in this package, for which query
          sync may need to build dependencies. (Any missing are detected when
          running tests).
    """

    # "sit" prefix stands for sync integration test.
    query_name = "sit_query"
    deps_name = "sit_deps"
    deps_desc_name = "sit_deps_desc"
    all_srcs_name = "sit_srcs_all"
    all_queries_group_name = "sit_queries_all_group"
    all_queries_name = "sit_queries_all"
    all_deps_name = "sit_deps_all"
    all_deps_descs_name = "sit_deps_desc_all"

    all_srcs_label = "//" + native.package_name() + ":" + all_srcs_name
    all_target_labels = ["//" + native.package_name() + r for r in all_targets]

    subpackages = native.subpackages(include = ["**"], allow_empty = True)
    subpackage_label_prefixes = ["//" + native.package_name() + "/" + p + ":" for p in subpackages]

    # A genquery target that simulates queries run by the query sync.
    # Note: It needs to be kep in sync with the query sync implementation.
    native.genquery(
        name = query_name,
        # The query sync runs queries in a form of
        # /directory/path/... + ... - /directory/path/...
        # This is not possible to do in genquery. Instead, we run a query that
        # consists of targets and sources parts and since there is no way to
        # have wildcards in dependency specification `test_project_root`
        # macro requires all targets to be listed manually, even though it can
        # later report any missing targets that matter.
        # Note: siblings() ensures that if any targets are missing from
        # all_target_labels they are still included in the query. If it
        # happens an error is reported when a test attempts to build
        # dependencies of such a target.
        expression = "siblings(" + " + ".join(all_target_labels) + ") + deps(" + all_srcs_label + ")",
        opts = ["--output=streamed_proto"],
        scope = all_targets + [all_srcs_label],
        visibility = ["//visibility:public"],
    )

    # A target that pre-builds all dependencies that the query sync may need to
    # build in this package.
    test_build_deps(
        name = deps_name,
        deps = all_target_labels,
        visibility = ["//visibility:public"],
    )

    # A rule that produces artifact_info_file file for all dependencies of this
    # package.
    test_build_deps_desc(
        name = deps_desc_name,
        deps = all_target_labels,
        visibility = ["//visibility:public"],
    )

    # A recursive filegroup that aggregates query results from this package and
    # its subpackages.
    native.filegroup(
        name = all_queries_group_name,
        srcs = [":" + query_name] +
               [p + query_name for p in subpackage_label_prefixes],
        visibility = ["//visibility:public"],
    )

    # A recursive filegroup that aggregates prebuilt dependencies from this
    # package and its subpackages.
    native.filegroup(
        name = all_deps_name,
        srcs = [":" + deps_name] +
               [p + deps_name for p in subpackage_label_prefixes],
        visibility = ["//visibility:public"],
    )

    # A recursive filegroup that aggregates artifact_info_file files from this
    # package and its subpackages.
    native.filegroup(
        name = all_deps_descs_name,
        srcs = [":" + deps_desc_name] +
               [p + deps_desc_name for p in subpackage_label_prefixes],
        visibility = ["//visibility:public"],
    )

    # A recursive filegroup that aggregates source  files from this package and
    # its subpackages.
    native.filegroup(
        name = all_srcs_name,
        srcs = native.glob(["**/*"]) +
               [p + all_srcs_name for p in subpackage_label_prefixes],
        tags = ["test_instrumentation"],
        visibility = ["//visibility:public"],
    )

    # A target that joins all query output files into one file.
    native.genrule(
        name = all_queries_name,
        srcs = [":" + all_queries_group_name],
        outs = ["all_queries"],
        cmd = "cat $(locations :" + all_queries_group_name + ") > $@",
    )

    # A target that produces the final testdata layout of a test project for use
    # in query sync integration tests.
    native.Fileset(
        name = "integration_test_data",
        out = "out",
        entries = [
            native.FilesetEntry(
                destdir = "sources",
                srcdir = "//" + native.package_name(),
                strip_prefix = "%workspace%",
                files = [":" + all_srcs_name],
            ),
            native.FilesetEntry(
                files = [":" + all_queries_name],
            ),
            native.FilesetEntry(
                destdir = "prebuilt_deps",
                strip_prefix = "%workspace%",
                files = [":" + all_deps_name],
            ),
            native.FilesetEntry(
                destdir = "target_prebuilt_dep_descs",
                strip_prefix = "",
                files = [":" + all_deps_descs_name],
            ),
        ],
        visibility = ["//visibility:public"],
    )
