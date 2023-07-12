load("@io_bazel_rules_kotlin//kotlin:jvm.bzl", "kt_jvm_binary", "kt_jvm_library")

kt_jvm_binary(
    name = "maintenance",
    srcs = glob(["maintenance.kt"]),
    main_class = "MaintenanceKt",
    deps = [
        "@com_google_guava_guava//jar",
    ],
)
