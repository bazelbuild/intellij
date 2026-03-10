load("@arm_none_eabi//toolchain:toolchain.bzl", "arm_none_eabi_toolchain")

arm_none_eabi_toolchain(
    name = "u575_toolchain",
    copts = [
        "--specs=nano.specs",
        "-mfpu=fpv5-sp-d16",
        "-mfloat-abi=hard",
        "-mthumb",
        "-mcpu=cortex-m33",
        "-std=gnu11",
        "-O0",
        "-ffunction-sections",
        "-fdata-sections",
        "-Wall",
        "-fstack-usage",
        "-DSTM32U575xx",
    ],
    linkopts = [
        "-mcpu=cortex-m33",
        "-mfpu=fpv4-sp-d16",
        "-mfloat-abi=hard",
        "--specs=nano.specs",
        "--specs=nosys.specs",
        "-Wl,--gc-sections",
        "-Wl,--print-memory-usage",
    ],
    target_compatible_with = [
        "@platforms//os:none",
        "@platforms//cpu:armv8-m",
    ],
)
