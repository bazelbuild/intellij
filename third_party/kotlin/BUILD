load("@rules_kotlin//kotlin:core.bzl", "define_kt_toolchain", "kt_kotlinc_options")

kt_kotlinc_options(
    name = "kotlinc_options",
    include_stdlibs = "none",
    jvm_target = "17",
)

# choose lowest api version depending on what we target atm:
# https://plugins.jetbrains.com/docs/intellij/using-kotlin.html#kotlin-standard-librar
define_kt_toolchain(
    name = "toolchain",
    api_version = "2.0",
    jvm_target = "17",
    kotlinc_options = ":kotlinc_options",
    language_version = "2.0",
)
