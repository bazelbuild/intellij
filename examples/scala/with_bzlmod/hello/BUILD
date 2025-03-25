load("@rules_scala//scala:scala.bzl", "scala_binary", "scala_library", "scala_test")

scala_library(
    name = "hello",
    srcs = ["Hello.scala"],
)

scala_test(
    name = "hello_test",
    size = "small",
    srcs = ["HelloTest.scala"],
    deps = [":hello"],
)

scala_binary(
    name = "hello_app",
    srcs = ["HelloApp.scala"],
    main_class = "HelloApp",
    deps = [":hello"],
)
