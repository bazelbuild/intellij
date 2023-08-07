java_binary(
    name = "client",
    main_class = "com.google.idea.blaze.ext.IntelliJExtClientCli",
    runtime_deps = [
        ":intellij_ext",
    ],
)

java_library(
    name = "intellij_ext",
    srcs = glob(["src/**/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//ext/proto:intellij_ext_java_grpc",
        "//ext/proto:intellij_ext_java_proto",
        "//third_party/java/grpc:core",
        "//third_party/java/grpc:netty",
        "//third_party/java/netty4:common",
        "//third_party/java/netty4:transport",
        "//third_party/java/netty4:transport_native_epoll",
        "//third_party/java/netty4:transport_native_unix_common",
    ],
)

java_binary(
    name = "IntelliJExtTestServer",
    srcs = ["tests/com/google/idea/blaze/ext/IntelliJExtTestServer.java"],
    main_class = "com.google.idea.blaze.ext.IntelliJExtTestServer",
    deps = [
        "//ext/proto:intellij_ext_java_grpc",
        "//ext/proto:intellij_ext_java_proto",
        "//third_party/java/grpc:core",
        "//third_party/java/grpc:netty",
        "//third_party/java/grpc:stub",
        "//third_party/java/netty4:common",
        "//third_party/java/netty4:transport",
        "//third_party/java/netty4:transport_native_epoll",
        "//third_party/java/netty4:transport_native_unix_common",
    ],
)

java_test(
    name = "IntelliJExtServiceTest",
    srcs = ["tests/com/google/idea/blaze/ext/IntelliJExtServiceTest.java"],
    data = [
        "//ext:IntelliJExtTestServer_deploy.jar",
    ],
    test_class = "com.google.idea.blaze.ext.IntelliJExtServiceTest",
    deps = [
        ":intellij_ext",
        "//ext/proto:intellij_ext_java_proto",
        "@junit//jar",
    ],
)
