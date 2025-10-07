load("@rules_kotlin//kotlin:core.bzl", "define_kt_toolchain", "kt_kotlinc_options")
load("//intellij_platform_sdk:build_defs.bzl", "select_for_plugin_api")

kt_kotlinc_options(
    name = "kotlinc_options",
    include_stdlibs = "none",
    jvm_target = "17",
)

# choose lowest api version depending on what we target atm:
# https://plugins.jetbrains.com/docs/intellij/using-kotlin.html#kotlin-standard-library
define_kt_toolchain(
    name = "toolchain",
    api_version = select_for_plugin_api({
        "intellij-2025.1": "2.0",
        "intellij-2025.2": "2.0",
        "intellij-2025.3": "2.3",
        "intellij-ue-2025.1": "2.0",
        "intellij-ue-2025.2": "2.0",
        "intellij-ue-2025.3": "2.3",
    }),
    jvm_target = "17",
    kotlinc_options = ":kotlinc_options",
    language_version = select_for_plugin_api({
        "intellij-2025.1": "2.0",
        "intellij-2025.2": "2.0",
        "intellij-2025.3": "2.3",
        "intellij-ue-2025.1": "2.0",
        "intellij-ue-2025.2": "2.0",
        "intellij-ue-2025.3": "2.3",
    }),
)
