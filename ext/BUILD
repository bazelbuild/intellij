java_binary(
    name = "client",
    main_class = "com.google.idea.blaze.ext.IntelliJExtClientCli",
    tags = ["manual"],
    runtime_deps = [
        ":intellijext",
    ],
)

java_library(
    name = "intellijext",
    srcs = glob(["src/**/*.java"]),
    tags = ["manual"],
    visibility = ["//visibility:public"],
    deps = [
        "//ext/proto:intellijext_java_grpc",
        "//ext/proto:intellijext_java_proto",
        "//third_party/java/grpc:core",
        "//third_party/java/grpc:netty",
        "//third_party/java/netty4:common",
        "//third_party/java/netty4:transport",
        "//third_party/java/netty4:transport_native_epoll",
        "//third_party/java/netty4:transport_native_unix_common",
        "@com_google_guava_guava//jar",
    ],
)

java_binary(
    name = "IntelliJExtTestServer",
    srcs = ["tests/com/google/idea/blaze/ext/IntelliJExtTestServer.java"],
    main_class = "com.google.idea.blaze.ext.IntelliJExtTestServer",
    tags = ["manual"],
    deps = [
        "//ext/proto:intellijext_java_grpc",
        "//ext/proto:intellijext_java_proto",
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
    tags = ["manual"],
    test_class = "com.google.idea.blaze.ext.IntelliJExtServiceTest",
    deps = [
        ":intellijext",
        "//ext/proto:intellijext_java_proto",
        "//third_party/java/grpc:core",
        "@junit//jar",
        "@truth//jar",
    ],
)
