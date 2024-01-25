load("@bazel_tools//tools/build_defs/repo:jvm.bzl", "jvm_maven_import_external")

JUNIT_JUPITER_VERSION = "5.9.2"

JUNIT_PLATFORM_VERSION = "1.9.2"

_JUNIT5_DEPS_TO_FETCH = [
    {
        "name": "junit_platform_launcher",
        "coords": "org.junit.platform:junit-platform-launcher:%s" % JUNIT_PLATFORM_VERSION,
        "artifact_sha256": "eef139eb09c98e9cdd358b6ce4c6cdd59b30c6a88096e369c33ba96e67edf0e4",
    },
    {
        "name": "junit_platform_reporting",
        "coords": "org.junit.platform:junit-platform-reporting:%s" % JUNIT_PLATFORM_VERSION,
        "artifact_sha256": "d6788db1c941c1247e07d8104f57c3f06175cadfd43060a792493fe9195671db",
    },
    {
        "name": "junit_platform_commons",
        "coords": "org.junit.platform:junit-platform-commons:%s" % JUNIT_PLATFORM_VERSION,
        "artifact_sha256": "624a3d745ef1d28e955a6a67af8edba0fdfc5c9bad680a73f67a70bb950a683d",
    },
    {
        "name": "junit_platform_engine",
        "coords": "org.junit.platform:junit-platform-engine:%s" % JUNIT_PLATFORM_VERSION,
        "artifact_sha256": "25f23dc535a091e9dc80c008faf29dcb92be902e6911f77a736fbaf019908367",
    },
    {
        "name": "junit_jupiter_api",
        "coords": "org.junit.jupiter:junit-jupiter-api:%s" % JUNIT_JUPITER_VERSION,
        "artifact_sha256": "f767a170f97127b0ad3582bf3358eabbbbe981d9f96411853e629d9276926fd5",
    },
    {
        "name": "junit_jupiter_params",
        "coords": "org.junit.jupiter:junit-jupiter-params:%s" % JUNIT_JUPITER_VERSION,
        "artifact_sha256": "bde91900a5ce5d6663bb44bc708494b35daefcd73e1bb7afa61a4affe38ea97d",
    },
    {
        "name": "junit_jupiter_engine",
        "coords": "org.junit.jupiter:junit-jupiter-engine:%s" % JUNIT_JUPITER_VERSION,
        "artifact_sha256": "74cfc49388f760413ff348ca2c9ab39527484b57deecd157f2275a5f8a5fe971",
    },
    {
        "name": "opentest4j",
        "coords": "org.opentest4j:opentest4j:1.2.0",
        "artifact_sha256": "58812de60898d976fb81ef3b62da05c6604c18fd4a249f5044282479fc286af2",
    },
]

JUNIT5_DEPS = ["@{name}//jar".format(name = dep["name"]) for dep in _JUNIT5_DEPS_TO_FETCH]

def instantiate_junit5_deps():
    for dep in _JUNIT5_DEPS_TO_FETCH:
        jvm_maven_import_external(
            name = dep["name"],
            artifact = dep["coords"],
            artifact_sha256 = dep["artifact_sha256"],
            licenses = ["notice"],  # Common Public License 1.0
            server_urls = ["https://repo1.maven.org/maven2"],
        )
